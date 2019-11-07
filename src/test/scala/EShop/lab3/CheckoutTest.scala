package EShop.lab3

import EShop.lab2.CartActor.CloseCheckout
import EShop.lab2.Checkout
import EShop.lab2.Checkout.{ReceivePayment, SelectDeliveryMethod, SelectPayment, StartCheckout}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class CheckoutTest
  extends TestKit(ActorSystem("CheckoutTest"))
  with FlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  it should "Send close confirmation to cart" in {
    val cartActor = TestProbe()
    val checkout  = cartActor.childActorOf(Props(new Checkout(cartActor.ref)))
    checkout ! StartCheckout
    checkout ! SelectDeliveryMethod("inpost")
    checkout ! SelectPayment("cash")
    checkout ! ReceivePayment
    cartActor.expectMsg(CloseCheckout)
  }

}
