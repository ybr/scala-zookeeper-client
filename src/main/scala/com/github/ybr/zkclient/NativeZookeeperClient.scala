package com.github.ybr.zkclient

import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}

import org.apache.zookeeper._
import org.apache.zookeeper.data.{ACL, Stat}

import scala.concurrent._
import scala.collection.JavaConverters._
import scala.language.higherKinds

class NativeZookeeperClient(val zk: ZooKeeper) extends ZooKeeperClient {

  def close() { zk.close() }

  def create[A](path: String, data: Array[Byte], acl: List[ACL], createMode: CreateMode, ctx: Option[A])(implicit ec: ExecutionContext): Future[CreateResponse[A]] = {
    val promise = Promise[CreateResponse[A]]()
    zk.create(
      path,
      data,
      acl.asJava,
      createMode,
      new AsyncCallback.StringCallback {
        def processResult(rc: Int, path: String, ctx: AnyRef, name: String) {
          val discard = promise.success(CreateResponse(KeeperException.Code.get(rc), path, ctx.asInstanceOf[Option[A]], name))
        }
      },
      ctx
    )
    promise.future
  }

  def delete[A](path: String, version: Int, ctx: Option[A])(implicit ec: ExecutionContext): Future[VoidResponse[A]] = {
    val promise = Promise[VoidResponse[A]]()
    zk.delete(
      path,
      version,
      new AsyncCallback.VoidCallback {
        def processResult(rc: Int, path: String, ctx: AnyRef) {
          val discard = promise.success(VoidResponse(KeeperException.Code.get(rc), path, ctx.asInstanceOf[Option[A]]))
        }
      },
      ctx
    )
    promise.future
  }

  def getACL(path: String, stat: Stat)(implicit ec: ExecutionContext): Future[ACLResponse[Nothing]] = getACL(path, stat, None)

  def getACL[A](path: String, stat: Stat, ctx: Option[A])(implicit ec: ExecutionContext): Future[ACLResponse[A]] = {
    val promise = Promise[ACLResponse[A]]()
    zk.getACL(
      path,
      stat,
      new AsyncCallback.ACLCallback {
        def processResult(rc: Int, path: String, ctx: AnyRef, acl: java.util.List[ACL], stat: Stat) {
          val discard = promise.success(ACLResponse(KeeperException.Code.get(rc), path, ctx.asInstanceOf[Option[A]], acl.asScala.toList, stat))
        }
      },
      ctx
    )
    promise.future
  }

  def setACL[A](path: String, acl: List[ACL], version: Int, ctx: Option[A])(implicit ec: ExecutionContext): Future[StatResponse[A]] = {
    val promise = Promise[StatResponse[A]]()
    zk.setACL(
      path,
      acl.asJava,
      version,
      new AsyncCallback.StatCallback {
        def processResult(rc: Int, path: String, ctx: AnyRef, stat: Stat) {
          val discard = promise.success(StatResponse(KeeperException.Code.get(rc), path, ctx.asInstanceOf[Option[A]], stat))
        }
      },
      ctx
    )
    promise.future
  }

  def getChildren(path: String)(implicit ec: ExecutionContext): Future[ChildrenResponse[Nothing]] = getChildren(path, None)

  def getChildren[A](path: String, ctx: Option[A])(implicit ec: ExecutionContext): Future[ChildrenResponse[A]] = {
    val promise = Promise[ChildrenResponse[A]]()
    zk.getChildren(
      path,
      null,
      new AsyncCallback.Children2Callback {
        def processResult(rc: Int, path: String, ctx: AnyRef, children: java.util.List[String], stat: Stat) {
          val discard = promise.success(ChildrenResponse(KeeperException.Code.get(rc), path, ctx.asInstanceOf[Option[A]], children.asScala.toList, stat))
        }
      },
      ctx
    )
    promise.future
  }

  def watchChildren[A](path: String, ctx: Option[A])(implicit ec: ExecutionContext): Future[(ChildrenResponse[A], Source[(ChildrenResponse[A], WatchedEvent), NotUsed])] = watch(watchChildrenStep[A])(path, ctx)

  private def watchChildrenStep[A](path: String, ctx: Option[A])(implicit ec: ExecutionContext): (Future[ChildrenResponse[A]], Future[WatchedEvent]) = {
    val promise = Promise[ChildrenResponse[A]]()
    val promiseEvent = Promise[WatchedEvent]()
    zk.getChildren(
      path,
      new Watcher {
        def process(event: WatchedEvent) {
          val discard = promiseEvent.success(event)
        }
      },
      new AsyncCallback.Children2Callback {
        def processResult(rc: Int, path: String, ctx: AnyRef, children: java.util.List[String], stat: Stat) {
          val discard = promise.success(ChildrenResponse(KeeperException.Code.get(rc), path, ctx.asInstanceOf[Option[A]], children.asScala.toList, stat))
        }
      },
      ctx
    )
    (promise.future, promiseEvent.future)
  }

  def getData(path: String)(implicit ec: ExecutionContext): Future[DataResponse[Nothing]] = for {
    (response, watchStream) <- watchData[Nothing](path, None)
    _ <- watchStream.runWith(Sink.ignore)
  } yield response

  def setData[A](path: String, data: Array[Byte], version: Int, ctx: Option[A])(implicit ec: ExecutionContext): Future[StatResponse[A]] = {
    val promise = Promise[StatResponse[A]]()
    zk.setData(
      path,
      data,
      version,
      new AsyncCallback.StatCallback {
        def processResult(rc: Int, path: String, ctx: AnyRef, stat: Stat) {
          val discard = promise.success(StatResponse(KeeperException.Code.get(rc), path, ctx.asInstanceOf[Option[A]], stat))
        }
      },
      ctx
    )
    promise.future
  }

  def watchData[A](path: String, ctx: Option[A])(implicit ec: ExecutionContext): Future[(DataResponse[A], Source[(DataResponse[A], WatchedEvent), NotUsed])] = watch(watchDataStep[A])(path, ctx)

  private def watchDataStep[A](path: String, ctx: Option[A])(implicit ec: ExecutionContext): (Future[DataResponse[A]], Future[WatchedEvent]) = {
    val promise = Promise[DataResponse[A]]()
    val promiseEvent = Promise[WatchedEvent]()
    zk.getData(
      path,
      new Watcher {
        def process(event: WatchedEvent) {
          val discard = promiseEvent.success(event)
        }
      },
      new AsyncCallback.DataCallback {
        def processResult(rc: Int, path: String, ctx: AnyRef, data: Array[Byte], stat: Stat) {
          val discard = promise.success(DataResponse(KeeperException.Code.get(rc), path, ctx.asInstanceOf[Option[A]], data, stat))
        }
      },
      ctx
    )
    (promise.future, promiseEvent.future)
  }

  def exists(path: String)(implicit ec: ExecutionContext): Future[StatResponse[Nothing]] = for {
     (response, watchStream) <- watchExists[Nothing](path, None)
     _ <- watchStream.runWith(Sink.ignore)
    } yield response

  def watchExists[A](path: String, ctx: Option[A])(implicit ec: ExecutionContext): Future[(StatResponse[A], Source[(StatResponse[A], WatchedEvent), NotUsed])] = watch(watchExistsStep[A])(path, ctx)

   private def watchExistsStep[A](path: String, ctx: Option[A])(implicit ec: ExecutionContext): (Future[StatResponse[A]], Future[WatchedEvent]) = {
    val promise = Promise[StatResponse[A]]()
    val promiseEvent = Promise[WatchedEvent]()
    zk.exists(
      path,
      new Watcher {
        def process(event: WatchedEvent) {
          val discard = promiseEvent.success(event)
        }
      },
      new AsyncCallback.StatCallback {
        def processResult(rc: Int, path: String, ctx: AnyRef, stat: Stat) {
          val discard = promise.success(StatResponse(KeeperException.Code.get(rc), path, ctx.asInstanceOf[Option[A]], stat))
        }
      },
      ctx
    )
    (promise.future, promiseEvent.future)
  }

  val sessionId: Long = zk.getSessionId()
  val sessionTimeout: Int = zk.getSessionTimeout()

  private def watch[A, R[_]](f: (String, Option[A]) => (Future[R[A]], Future[WatchedEvent]))(path: String, ctx: Option[A])(implicit ec: ExecutionContext): Future[(R[A], Source[(R[A], WatchedEvent), NotUsed])] = {
    val (eventualResponse, eventualWatchedEvent) = f(path, ctx)

    val watchStream: Source[(R[A], WatchedEvent), NotUsed] = Source.unfoldAsync(eventualWatchedEvent) { eventualWatchedEvent =>
      for {
        watchedEvent <- eventualWatchedEvent
        (newEventualResponse, newEventualWatchedEvent) = f(path, ctx)
        newResponse <- newEventualResponse
      } yield Option((newEventualWatchedEvent, (newResponse, watchedEvent)))
    }

    for {
      response <- eventualResponse
    } yield (response, watchStream)
  }
}