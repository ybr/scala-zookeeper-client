package com.github.ybr.zk.recipes

import akka.stream.Materializer
import akka.stream.scaladsl.Sink

import com.github.ybr.zk.client._

import java.util.UUID

import org.apache.commons.logging.LogFactory

import org.apache.zookeeper._
import org.apache.zookeeper.data.ACL

import scala.concurrent._

/*
 * From https://zookeeper.apache.org/doc/trunk/recipes.html#sc_leaderElection
 *
 * Let ELECTION be a path of choice of the application. To volunteer to be a leader:
 * 1. Create znode z with path "ELECTION/guid-n_" with both SEQUENCE and EPHEMERAL flags;
 * 2. Let C be the children of "ELECTION", and i be the sequence number of z;
 * 3. Watch for changes on "ELECTION/guid-n_j", where j is the largest sequence number such that j < i and n_j is a znode in C;
 *
 * Upon receiving a notification of znode deletion:
 * 1. Let C be the new set of children of ELECTION;
 * 2. If z is the smallest node in C, then execute leader procedure;
 * 3. Otherwise, watch for changes on "ELECTION/guid-n_j", where j is the largest sequence number such that j < i and n_j is a znode in C;
 *
 * TODO
 * Notes:
 * - Note that the znode having no preceding znode on the list of children does not imply that the creator of this znode is aware that it is the current leader. Applications may consider creating a separate znode to acknowledge that the leader has executed the leader procedure.
 * - If a recoverable error occurs calling create() the client should call getChildren() and check for a node containing the guid used in the path name. This handles the case (noted above) of the create() succeeding on the server but the server crashing before returning the name of the new node.
 */
object LeaderElection {
  private val log = LogFactory.getLog(LeaderElection.getClass)

  def createRoom(path: String)(implicit zk: ZooKeeperClient, ec: ExecutionContext): Future[ElectionRoom] = for {
    acl <- zk.getACL[Nothing]("/", null, None).map(_.acl)
    prefixes = path.split('/').filter(_.length > 0).scanLeft(List("")) { (prev, curr) =>
      prev :+ curr
    }.drop(1).map(_.mkString("/"))
    parents = prefixes.foldLeft(Future.successful(())) { (prev, parentPath) =>
      for {
        _ <- prev
        _ <- zk.create[Nothing](parentPath, Array.empty, acl, CreateMode.PERSISTENT, None)
      } yield ()
    }
    roomPath = s"${path}/election"
    _ <- zk.create[Nothing](roomPath, Array.empty, acl, CreateMode.PERSISTENT, None)
  } yield new ElectionRoom(roomPath, acl)
}

object ElectionRoom {
  private val log = LogFactory.getLog(ElectionRoom.getClass)
}

class ElectionRoom(val path: String, val acl: List[ACL]) {
  def beVolunteer(uuid: UUID)(implicit zk: ZooKeeperClient, ec: ExecutionContext, mat: Materializer): Future[Unit] = {
    ElectionRoom.log.debug(s"I am volunteer ${uuid} in ${path}")
    for {
      _ <- candidate(uuid)
      volunteers <- retrieveVolunteers()
      _ <- elect(uuid, volunteers)
    } yield ()
  }

  private def elect(uuid: UUID, volunteers: List[(UUID, String)])(implicit zk: ZooKeeperClient, ec: ExecutionContext, mat: Materializer): Future[Unit] = {
    val maybeLeader = whoIsTheLeader(uuid, volunteers)
    ElectionRoom.log.debug(s"Leader is ${maybeLeader.map(_._1).getOrElse("unknown")} in ${path}")

    val elected = maybeLeader.map(_._1 == uuid).getOrElse(false)

    if(elected) {
      ElectionRoom.log.debug(s"Elected ${uuid}")
      Future.successful(())
    }
    else {
      ElectionRoom.log.debug(s"Not elected ${uuid}, waiting my turn...")
      precedingVolunteer(uuid, volunteers) match {
        case Some((precedingVolunteerUUID, precedingVolunteerSeqId)) =>
          val precedingZNode = s"${path}/${precedingVolunteerUUID}_${precedingVolunteerSeqId}"
          ElectionRoom.log.debug(s"Waiting for ${precedingVolunteerUUID} in ${path} to be ejected...")
          zk.watchExists[Nothing](precedingZNode, None).flatMap { case (existResponse, existWatchStream) =>
            if(KeeperException.Code.OK == existResponse.rc) {
              existWatchStream.runWith(Sink.head).flatMap { case (response, event) =>
                ElectionRoom.log.debug("May become leader, replay election")
                for {
                  volunteers <- retrieveVolunteers()
                  _ <- elect(uuid, volunteers)
                } yield ()
              }
            }
            else {
              Future.failed(new RuntimeException("No preceding volunteer"))
            }
          }
        case None =>
          ElectionRoom.log.debug("Not leader and no preceding volunteer => be volunter")
          beVolunteer(uuid)
      }
    }
  }

  private def candidate(uuid: UUID)(implicit zk: ZooKeeperClient, ec: ExecutionContext): Future[Unit] = {
    ElectionRoom.log.debug(s"Candidate ${uuid} in ${path}")
    zk.create[Nothing](s"${path}/${uuid}_", Array.empty, acl, CreateMode.EPHEMERAL_SEQUENTIAL, None).map(_ => ())
  }

  private val volunteerId = "(.+)_([0-9]+)".r

  private def retrieveVolunteers()(implicit ec: ExecutionContext, zk: ZooKeeperClient): Future[List[(UUID, String)]] = zk.getChildren[Nothing](path, None).map { childrenResponse =>
    childrenResponse.children.map {
      case volunteerId(volunteerUUID, volunteerSeqId) => (UUID.fromString(volunteerUUID), volunteerSeqId)
    }.sortBy(_._2)
  }

  private def whoIsTheLeader(uuid: UUID, volunteers: List[(UUID, String)]): Option[(UUID, String)] = volunteers.headOption

  private def precedingVolunteer(uuid: UUID, volunteers: List[(UUID, String)]): Option[(UUID, String)] = for {
      mySeqId <- volunteers.find(_._1 == uuid).headOption.map(_._2)
      previousVolunteers = volunteers.filter(_._2 < mySeqId)
      greatestVolunteer <- previousVolunteers.lastOption
    } yield greatestVolunteer
}