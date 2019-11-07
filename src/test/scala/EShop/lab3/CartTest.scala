package EShop.lab3

import EShop.lab2.{Cart, CartActor}
import EShop.lab2.CartActor.{AddItem, CheckoutStarted, GetItems, RemoveItem, StartCheckout}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class CartTest
  extends TestKit(ActorSystem("CartTest"))
  with FlatSpecLike
  with ImplicitSender
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val cartActor: TestActorRef[CartActor] = TestActorRef(new CartActor)
    cartActor ! AddItem("Ogniem i Mieczem")
    cartActor.receive(GetItems, self)
    expectMsg(Cart(Seq("Ogniem i Mieczem")))
  }

  it should "be empty after adding and removing the same item" in {
    val cartActor: TestActorRef[CartActor] = TestActorRef(new CartActor)
    cartActor ! AddItem("Ogniem i Mieczem")
    cartActor ! RemoveItem("Ogniem i Mieczem")
    cartActor.receive(GetItems, self)
    expectMsg(Cart(Seq.empty))
  }

  it should "start checkout" in {
    val cartActor: TestActorRef[CartActor] = TestActorRef(new CartActor)
    cartActor ! AddItem("Ogniem i Mieczem")
    cartActor ! StartCheckout
    fishForMessage() {
      case _: CheckoutStarted => true
      case _                  => false
    }
  }
}
