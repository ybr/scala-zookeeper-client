package com.github.ybr

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

package object zkclient {
  implicit val defaultActorSystem = ActorSystem.create()
  implicit def defaultMaterializer(implicit system: ActorSystem) = ActorMaterializer.create(system);
}