package EShop.lab2

import EShop.lab2.CartActor.CloseCheckout
import EShop.lab3.Payment
import akka.actor.{Actor, ActorRef, Cancellable, Props, Scheduler}
import akka.event.LoggingReceive

import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ReceivePayment                      extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

  def props(cart: ActorRef) = Props(new Checkout(cart))
}

class Checkout(cartActor: ActorRef) extends Actor {

  import Checkout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds
  private val scheduler: Scheduler          = context.system.scheduler

  def receive: Receive = LoggingReceive {
    case StartCheckout =>
      context become selectingDelivery(scheduleTimer(checkoutTimerDuration, ExpireCheckout))
  }

  def selectingDelivery(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout | ExpireCheckout =>
      timer.cancel()
      cartActor ! CartActor.CancelCheckout
      context become cancelled
    case SelectDeliveryMethod(_) =>
      context become selectingPaymentMethod(timer)
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout | ExpireCheckout =>
      timer.cancel()
      cartActor ! CartActor.CancelCheckout
      context become cancelled
    case SelectPayment(method) =>
      timer.cancel()
      val payment = context.actorOf(Payment.props(method, sender, self), "payment")
      sender ! PaymentStarted(payment)
      context become processingPayment(scheduleTimer(paymentTimerDuration, ExpirePayment))
  }

  def processingPayment(timer: Cancellable): Receive = LoggingReceive {
    case CancelCheckout | ExpirePayment =>
      timer.cancel()
      cartActor ! CartActor.CancelCheckout
      context become cancelled
    case ReceivePayment =>
      timer.cancel()
      cartActor ! CloseCheckout
      context become closed
  }

  def closed: Receive = LoggingReceive {
    case CloseCheckout =>
      context.stop(self)
  }

  def cancelled: Receive = LoggingReceive {
    case CancelCheckout =>
      context.stop(self)
  }

  private def scheduleTimer(finiteDuration: FiniteDuration, command: Command): Cancellable =
    scheduler.scheduleOnce(finiteDuration, self, command)(context.dispatcher, self)
}