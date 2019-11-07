package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.Logging

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

  def props(cart: ActorRef) = Props(new Checkout())
}

class Checkout extends Actor {
  import context._

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  val checkoutTimerDuration = 1 seconds
  val paymentTimerDuration  = 1 seconds

  private def scheduleCheckoutTimer: Cancellable = scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)
  private def schedulePaymentTimer: Cancellable  = scheduler.scheduleOnce(checkoutTimerDuration, self, ExpirePayment)

  def receive: Receive = {
    case StartCheckout => become(selectingDelivery(scheduleCheckoutTimer))
  }

  def selectingDelivery(timer: Cancellable): Receive = {
    case CancelCheckout | ExpireCheckout => become(cancelled)
    case SelectDeliveryMethod(method)    => become(selectingPaymentMethod(timer))
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = {
    case CancelCheckout | ExpireCheckout => become(cancelled)
    case SelectPayment(payment)          => become(processingPayment(schedulePaymentTimer))
  }

  def processingPayment(timer: Cancellable): Receive = {
    case CancelCheckout | ExpirePayment => become(cancelled)
    case ReceivePayment                 => become(closed)
  }

  def cancelled: Receive = PartialFunction.empty

  def closed: Receive = PartialFunction.empty

}
