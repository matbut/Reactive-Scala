package EShop.lab2

import EShop.lab2.Checkout._
import EShop.lab2.CheckoutFSM.Status
import akka.actor.{ActorRef, LoggingFSM, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object CheckoutFSM {

  object Status extends Enumeration {
    type Status = Value
    val NotStarted, SelectingDelivery, SelectingPaymentMethod, Cancelled, ProcessingPayment, Closed = Value
  }

  def props(cartActor: ActorRef) = Props(new CheckoutFSM(cartActor))
}

class CheckoutFSM(cartActor: ActorRef) extends LoggingFSM[Status.Value, Data] {
  import EShop.lab2.CheckoutFSM.Status._
  import context._

  // useful for debugging, see: https://doc.akka.io/docs/akka/current/fsm.html#rolling-event-log
  override def logDepth = 12

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  private val scheduler = context.system.scheduler

  startWith(NotStarted, Uninitialized)

  when(NotStarted) {
    case Event(StartCheckout, _) =>
      scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout); goto(SelectingDelivery)
  }

  when(SelectingDelivery) {
    case Event(CancelCheckout, _) | Event(ExpireCheckout, _) => goto(Cancelled)
    case Event(SelectDeliveryMethod(method), _)              => goto(SelectingPaymentMethod)
  }

  when(SelectingPaymentMethod) {
    case Event(CancelCheckout, _) | Event(ExpireCheckout, _) => goto(Cancelled)
    case Event(SelectPayment(payment), _) =>
      scheduler.scheduleOnce(checkoutTimerDuration, self, ExpirePayment); goto(ProcessingPayment)
  }

  when(ProcessingPayment) {
    case Event(CancelCheckout, _) | Event(ExpireCheckout, _) => goto(Cancelled)
    case Event(ReceivePayment, _)                            => goto(Closed)
  }

  when(Cancelled) {
    PartialFunction.empty
  }

  when(Closed) {
    PartialFunction.empty
  }

}
