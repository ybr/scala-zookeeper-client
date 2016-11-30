package com.github.ybr.zk.client

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source

import org.apache.zookeeper._
import org.apache.zookeeper.data.{ACL, Stat}

import scala.concurrent._

trait ZooKeeperClient {
  def close(): Unit

  def create[A](path: String, data: Array[Byte], acl: List[ACL], createMode: CreateMode, ctx: Option[A])(implicit ec: ExecutionContext): Future[CreateResponse[A]]
  def delete[A](path: String, version: Int, ctx: Option[A])(implicit ec: ExecutionContext): Future[VoidResponse[A]] 

  def exists(path: String)(implicit ec: ExecutionContext): Future[StatResponse[Nothing]]
  def watchExists[A](path: String, ctx: Option[A])(implicit ec: ExecutionContext): Future[(StatResponse[A], Source[(StatResponse[A], WatchedEvent), NotUsed])]

  def getACL[A](path: String, stat: Stat, ctx: Option[A])(implicit ec: ExecutionContext): Future[ACLResponse[A]]
  def setACL[A](path: String, acl: List[ACL], version: Int, ctx: Option[A])(implicit ec: ExecutionContext): Future[StatResponse[A]]

  def getChildren[A](path: String, ctx: Option[A])(implicit ec: ExecutionContext): Future[ChildrenResponse[A]]
  def watchChildren[A](path: String, ctx: Option[A])(implicit ec: ExecutionContext): Future[(ChildrenResponse[A], Source[(ChildrenResponse[A], WatchedEvent), NotUsed])]

  def getData(path: String)(implicit ec: ExecutionContext): Future[DataResponse[Nothing]]
  def setData[A](path: String, data: Array[Byte], version: Int, ctx: Option[A])(implicit ec: ExecutionContext): Future[StatResponse[A]]
  def watchData[A](path: String, ctx: Option[A])(implicit ec: ExecutionContext): Future[(DataResponse[A], Source[(DataResponse[A], WatchedEvent), NotUsed])]

  def sessionId(): Long
  def sessionTimeout(): Int
}

object ZooKeeperClient {
  def native(hosts: String, sessionTimeout: Int, canBeReadOnly: Boolean, watcher: Option[Watcher] = None)(implicit mat: Materializer) = {
    val zk = new ZooKeeper(hosts, sessionTimeout, watcher.getOrElse(null), canBeReadOnly)
    new NativeZookeeperClient(zk)
  }
}

case class ACLResponse[A](rc: KeeperException.Code, path: String, ctx: Option[A], acl: List[ACL], stat: Stat)
case class ChildrenResponse[A](rc: KeeperException.Code, path: String, ctx: Option[A], children: List[String], stat: Stat)
case class CreateResponse[A](rc: KeeperException.Code, path: String, ctx: Option[A], name: String)
case class DataResponse[A](rc: KeeperException.Code, path: String, ctx: Option[A], data: Array[Byte], stat: Stat)
case class VoidResponse[A](rc: KeeperException.Code, path: String, ctx: Option[A])
case class StatResponse[A](rc: KeeperException.Code, path: String, ctx: Option[A], stat: Stat)