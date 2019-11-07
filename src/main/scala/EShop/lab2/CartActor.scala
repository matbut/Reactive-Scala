package EShop.lab2

import EShop.lab2.CartActor._
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.Logging

import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)    extends Command
  case class RemoveItem(item: Any) extends Command
  case object ExpireCart           extends Command
  case object StartCheckout        extends Command
  case object CancelCheckout       extends Command
  case object CloseCheckout        extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {
  import context._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer: Cancellable =
    context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive = {
    case AddItem(item) => become(nonEmpty(Cart(Seq(item)), scheduleTimer))
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = {
    case RemoveItem(item) if cart.size == 1 && cart.contains(item) => become(empty)
    case ExpireCart                                                => become(empty)
    case AddItem(item)                                             => become(nonEmpty(cart.addItem(item), timer))
    case RemoveItem(item) if cart.contains(item)                   => become(nonEmpty(cart.removeItem(item), timer))
    case StartCheckout                                             => become(inCheckout(cart))
  }

  def inCheckout(cart: Cart): Receive = {
    case CancelCheckout => become(nonEmpty(cart, scheduleTimer))
    case CloseCheckout  => become(empty)
  }

}
