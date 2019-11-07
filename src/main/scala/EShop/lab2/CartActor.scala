package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props, Timers}
import akka.event.LoggingReceive

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
  case object GetItems             extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props() = Props(new CartActor())
}

class CartActor extends Actor with Timers {

  import CartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  def receive: Receive = LoggingReceive {
    empty
  }

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
    case GetItems =>
      sender ! Cart.empty
      context become empty
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      timer.cancel()
      context become nonEmpty(cart.addItem(item), scheduleTimer)
    case RemoveItem(item) if cart.contains(item) && cart.size > 1 =>
      timer.cancel()
      context become nonEmpty(cart.removeItem(item), scheduleTimer)
    case RemoveItem(item) if cart.contains(item) && cart.size == 1 =>
      timer.cancel()
      context become empty
    case ExpireCart =>
      context become empty
    case StartCheckout =>
      timer.cancel()
      val checkout = context.actorOf(Checkout.props(self), "checkout")
      checkout ! Checkout.StartCheckout
      sender ! CheckoutStarted(checkout)
      context become inCheckout(cart)
    case GetItems =>
      sender ! cart
      context become nonEmpty(cart, timer)
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CancelCheckout =>
      context.child("checkout").foreach(_ ! Checkout.CancelCheckout)
      context become nonEmpty(cart, scheduleTimer)
    case CloseCheckout =>
      context.child("checkout").foreach(_ ! CloseCheckout)
      context become empty
    case GetItems =>
      sender ! cart
      context become inCheckout(cart)
  }

  private def scheduleTimer: Cancellable =
    context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)(context.dispatcher, self)
}