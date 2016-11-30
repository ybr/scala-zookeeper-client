import com.github.ybr.zk.client._
import com.github.ybr.zk.recipes._

import akka.actor.ActorSystem

import java.util.UUID

import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.language.postfixOps

object Application {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem.create("myApp")
    implicit val zk = ZooKeeperClient.native("127.0.0.1:2181", 3000, true)

    val myUUID = UUID.randomUUID()
    Await.result(
      for {
        room <- LeaderElection.createRoom("/ica/counters")
        elected <- room.beVolunteer(myUUID)
      } yield (),
      Duration.Inf
    )

    Thread.sleep(60 * 1000)

    zk.close()

    val terminated = Await.result(system.terminate(), 10 seconds)
  }
}