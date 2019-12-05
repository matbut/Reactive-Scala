package EShop.lab4

import EShop.lab2.CartActor.CloseCheckout
import EShop.lab2.{CartActor, Checkout}
import EShop.lab3.Payment
import akka.actor.{ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.{PersistentActor, SnapshotOffer}

import scala.concurrent.duration._

object PersistentCheckout {

  def props(cartActor: ActorRef, persistenceId: String) =
    Props(new PersistentCheckout(cartActor, persistenceId))
}

class PersistentCheckout(
  cartActor: ActorRef,
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.Checkout._
  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)
  val timerDuration     = 1.seconds

  private def scheduleTimer(command: Command): Cancellable =
    scheduler.scheduleOnce(timerDuration, self, command)(context.dispatcher, self)

  private def updateState(event: Event, maybeTimer: Option[Cancellable] = None): Unit = {
    event match {
      case CheckoutStarted => context become selectingDelivery(scheduleTimer(ExpireCheckout))
      case DeliveryMethodSelected(_) =>
        context become selectingPaymentMethod(maybeTimer.getOrElse(scheduleTimer(ExpireCheckout)))
      case Checkout.CheckoutClosed => context become closed
      case CheckoutCancelled       => context become cancelled
      case PaymentStarted(_)       => context become processingPayment(scheduleTimer(ExpirePayment))
    }
  }

  def receiveCommand: Receive = LoggingReceive {
    case StartCheckout =>
      persist(CheckoutStarted)(updateState(_))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout | ExpireCheckout =>
      timer.cancel()
      cartActor ! CartActor.CancelCheckout
      persist(CheckoutCancelled)(updateState(_))
    case SelectDeliveryMethod(method) =>
      persist(DeliveryMethodSelected(method))(updateState(_, Option(timer)))
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout | ExpireCheckout =>
      timer.cancel()
      cartActor ! CartActor.CancelCheckout
      persist(CheckoutCancelled)(updateState(_))
    case SelectPayment(method) =>
      timer.cancel()
      val payment = context.actorOf(Payment.props(method, sender, self), "payment")
      val paymentStarted = PaymentStarted(payment)
      persist(paymentStarted) { event =>
        sender ! event
        updateState(event)
      }
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout | ExpirePayment =>
      timer.cancel()
      cartActor ! CartActor.CancelCheckout
      persist(CheckoutCancelled)(updateState(_))
    case ReceivePayment =>
      timer.cancel()
      cartActor ! CloseCheckout
      persist(Checkout.CheckoutClosed)(updateState(_))
  }

  def closed: Receive = LoggingReceive {
    case CloseCheckout =>
      context.stop(self)
  }

  def cancelled: Receive = LoggingReceive {
    case CancelCheckout =>
      context.stop(self)
  }

  override def receiveRecover: Receive = LoggingReceive {
    case event: Event         => updateState(event)
    case offer: SnapshotOffer => log.warning(s"Received unhandled snapshot offer $offer")
  }
}
