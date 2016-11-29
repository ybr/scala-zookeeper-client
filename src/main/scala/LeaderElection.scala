package com.github.ybr.zkclient

import akka.stream.scaladsl.{Flow, Sink}

import java.util.UUID

import org.apache.zookeeper._
import org.apache.zookeeper.data.ACL

import scala.concurrent._

/*
    Let ELECTION be a path of choice of the application. To volunteer to be a leader:
    Create znode z with path "ELECTION/guid-n_" with both SEQUENCE and EPHEMERAL flags;
    Let C be the children of "ELECTION", and i be the sequence number of z;
    Watch for changes on "ELECTION/guid-n_j", where j is the largest sequence number such that j < i and n_j is a znode in C;

    Upon receiving a notification of znode deletion:
      Let C be the new set of children of ELECTION;

    If z is the smallest node in C, then execute leader procedure;
    Otherwise, watch for changes on "ELECTION/guid-n_j", where j is the largest sequence number such that j < i and n_j is a znode in C;
*/
object LeaderElection {
  def createRoom(path: String)(implicit ec: ExecutionContext, zk: ZooKeeperClient): Future[ElectionRoom] = for {
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
  } yield ElectionRoom(roomPath, acl)
}

case class ElectionRoom(path: String, acl: List[ACL]) {
  def beVolunteer(uuid: UUID)(implicit ec: ExecutionContext, zk: ZooKeeperClient): Future[Unit] = {
    println(s"Volunteer ${uuid} for ${path}")
    for {
      _ <- candidate(uuid)
      volunteers <- retrieveVolunteers()
      _ <- elect(uuid, volunteers)
    } yield ()
  }

  def elect(uuid: UUID, volunteers: List[(UUID, String)])(implicit ec: ExecutionContext, zk: ZooKeeperClient): Future[Unit] = {
    val maybeLeader = whoIsTheLeader(uuid, volunteers)
    println(s"Leader is ${maybeLeader}")

    val elected = maybeLeader.map(_._1 == uuid).getOrElse(false)

    if(elected) {
      println(s"Elected ${uuid}")
      Future.successful(())
    }
    else {
      println(s"Not elected ${uuid}, waiting my turn...")
      precedingVolunteer(uuid, volunteers) match {
        case Some((precedingVolunteerUUID, precedingVolunteerSeqId)) =>
          val precedingZNode = s"${path}/${precedingVolunteerUUID}_${precedingVolunteerSeqId}"
          println(s"Waiting for ${precedingZNode} to be ejected...")
          zk.watchExists[Nothing](precedingZNode, None).flatMap { case (existResponse, existWatchStream) =>
            if(KeeperException.Code.OK == existResponse.rc) {
              existWatchStream.via(Flow[(StatResponse[Nothing], WatchedEvent)].take(1)).runWith(Sink.head).flatMap { case (response, event) =>
                println("may become leader " + response + " " + event)
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
          println("Not leader and no preceding volunteer => be volunter")
          beVolunteer(uuid)
      }
    }
  }

  def candidate(uuid: UUID)(implicit ec: ExecutionContext, zk: ZooKeeperClient): Future[Unit] = {
    println(s"Canidate ${uuid} for ${path}")
    zk.create[Nothing](s"${path}/${uuid}_", Array.empty, acl, CreateMode.EPHEMERAL_SEQUENTIAL, None).map(_ => ())
  }

  val volunteerId = "(.+)_([0-9]+)".r

  def retrieveVolunteers()(implicit ec: ExecutionContext, zk: ZooKeeperClient): Future[List[(UUID, String)]] = zk.getChildren[Nothing](path, None).map { childrenResponse =>
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