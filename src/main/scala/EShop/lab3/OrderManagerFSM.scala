package EShop.lab3

import EShop.lab2.CartActor.CheckoutStarted
import EShop.lab2.Checkout.PaymentStarted
import EShop.lab2.{CartActor, Checkout}
import EShop.lab3.OrderManager._
import EShop.lab3.Payment.DoPayment
import akka.actor.{ActorRef, FSM}

class OrderManagerFSM extends FSM[State, Data] {

  startWith(Uninitialized, Empty)

  when(Uninitialized) {
    case Event(AddItem(item), Empty) =>
      val cart: ActorRef = context.actorOf(CartActor.props(), "cart")
      cart ! CartActor.AddItem(item)
      sender ! Done
      goto(Open).using(CartData(cart))
  }

  when(Open) {
    case Event(AddItem(item), CartData(cartActor)) =>
      cartActor ! CartActor.AddItem(item)
      sender ! Done
      stay
    case Event(RemoveItem(item), CartData(cartActor)) =>
      cartActor ! CartActor.RemoveItem(item)
      sender ! Done
      stay
    case Event(Buy, CartData(cartActor)) =>
      cartActor ! CartActor.StartCheckout
      goto(InCheckout).using(CartDataWithSender(cartActor, sender))
  }

  when(InCheckout) {
    case Event(CheckoutStarted(checkoutRef, _), CartDataWithSender(_, senderRef)) =>
      senderRef ! Done
      stay.using(InCheckoutData(checkoutRef))
    case Event(SelectDeliveryAndPaymentMethod(delivery, payment), InCheckoutData(checkoutActorRef)) =>
      checkoutActorRef ! Checkout.SelectDeliveryMethod(delivery)
      checkoutActorRef ! Checkout.SelectPayment(payment)
      goto(InPayment).using(InCheckoutDataWithSender(checkoutActorRef, sender))
  }

  when(InPayment) {
    case Event(PaymentStarted(paymentRef), InCheckoutDataWithSender(_, senderRef)) =>
      senderRef ! Done
      stay.using(InPaymentData(paymentRef))
    case Event(Pay, InPaymentData(paymentActorRef)) =>
      paymentActorRef ! DoPayment
      stay.using(InPaymentDataWithSender(paymentActorRef, sender))
    case Event(Payment.PaymentConfirmed, InPaymentDataWithSender(_, senderRef)) =>
      senderRef ! Done
      goto(Finished).using(Empty)
  }

  when(Finished) {
    case _ =>
      sender ! "order manager finished job"
      stay()
  }
}