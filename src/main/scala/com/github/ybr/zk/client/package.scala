package com.github.ybr.zk

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}

package object client {
  implicit def defaultMaterializer(implicit system: ActorSystem): Materializer = ActorMaterializer.create(system);
}