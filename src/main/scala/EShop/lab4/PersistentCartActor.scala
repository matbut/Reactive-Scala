package EShop.lab4

import EShop.lab2.{Cart, Checkout}
import akka.actor.{Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.{PersistentActor, SnapshotOffer}

import scala.concurrent.duration._
import scala.util.Random

object PersistentCartActor {

  def props(persistenceId: String) = Props(new PersistentCartActor(persistenceId))
}

class PersistentCartActor(
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5.seconds

  private def scheduleTimer: Cancellable =
    context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)(context.dispatcher, self)

  override def receiveCommand: Receive = empty

  private def updateState(event: Event, timer: Option[Cancellable] = None): Unit = {
    timer.foreach(_.cancel)
    event match {
      case CartExpired | CheckoutClosed => context become empty
      case CheckoutCancelled(cart)      => context become nonEmpty(cart, scheduleTimer)
      case ItemAdded(item, cart)        => context become nonEmpty(cart.addItem(item), scheduleTimer)
      case CartEmptied                  => context become empty
      case ItemRemoved(item, cart)      => context become nonEmpty(cart.removeItem(item), scheduleTimer)
      case CheckoutStarted(_, cart)     => context become inCheckout(cart)
    }
  }

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      persist(ItemAdded(item, Cart.empty))(updateState(_))
    case GetItems =>
      sender ! Cart.empty
      context become empty
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case AddItem(item) =>
      persist(ItemAdded(item, cart))(updateState(_, Option(timer)))
    case RemoveItem(item) if cart.contains(item) && cart.size > 1 =>
      persist(ItemRemoved(item, cart))(updateState(_, Option(timer)))
    case RemoveItem(item) if cart.contains(item) && cart.size == 1 =>
      persist(CartEmptied)(updateState(_, Option(timer)))
    case ExpireCart =>
      persist(CartExpired)(updateState(_, Option(timer)))
    case StartCheckout =>
      val checkout =
        context.actorOf(PersistentCheckout.props(self, Random.alphanumeric.take(256).mkString), "checkout")
      checkout ! Checkout.StartCheckout
      val checkoutStarted = CheckoutStarted(checkout, cart)
      persist(checkoutStarted) { event =>
        sender ! event
        updateState(event, Option(timer))
      }
    case GetItems =>
      sender ! cart
      context become nonEmpty(cart, timer)
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CancelCheckout =>
      context.child("checkout").foreach(_ ! Checkout.CancelCheckout)
      persist(CheckoutCancelled(cart))(updateState(_))
    case CloseCheckout =>
      context.child("checkout").foreach(_ ! CloseCheckout)
      persist(CheckoutClosed)(updateState(_))
    case GetItems =>
      sender ! cart
      context become inCheckout(cart)
  }

  override def receiveRecover: Receive = LoggingReceive {
    case event: Event         => updateState(event)
    case offer: SnapshotOffer => log.warning(s"Received unhandled snapshot offer $offer")
  }
}
