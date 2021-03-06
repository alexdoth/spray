/*
 * Copyright (C) 2011, 2012 Mathias Doenitz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akka.spray

import akka.actor._

/**
 * An ActorRef which
 * - offers the ability to hook caller-side logic into a `tell`
 * - is never registered anywhere, i.e. can be GCed as soon the receiver drops it or is GCed itself
 *
 * CAUTION: This ActorRef is _not_ addressable from a non-local JVM and it also breaks some otherwise
 * valid invariants like `system.actorFor(ref.path.toString).equals(ref)` in the local-only context.
 * It should therefore be used only in purely local environments and in consideration of the limitations.
 */
abstract class UnregisteredActorRef(provider: ActorRefProvider) extends LazyActorRef(provider) {
  def this(related: ActorRef) = this(RefUtils.provider(related))
  def this(system: ActorSystem) = this {
    system match {
      case x: ExtendedActorSystem => x.provider
      case _ => throw new IllegalArgumentException("Unsupported ActorSystem implementation")
    }
  }
  def this(context: ActorContext) = this(context.system)

  override protected def register(path: ActorPath) {}

  override protected def unregister(path: ActorPath) {}
}
