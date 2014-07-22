package models

import akka.actor._
import scala.concurrent.duration._

import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.Play.current

import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/** Factory for creating a [[models.Websocket]] actor. */
object Websocket {
  implicit val timeout = Timeout(1 second)

  lazy val default = {
    val roomActor = Akka.system.actorOf(Props[Websocket], "WebSocket")

    roomActor
  }

  def send(id: String, msg: JsValue) = {
    default ! Send(id, msg)
  }

  /** Create a websocket for every joining visitor/user, send initial message. */
  def join(id: String, projectPath: String): Future[(Iteratee[JsValue, Unit], Enumerator[JsValue])] = {
    (default ? Join(id, projectPath)).map {

      case Connected(enumerator) => {
        // Create an Iteratee to consume the feed
        val iteratee = Iteratee.foreach[JsValue] { event =>
          default ! Talk(id, (event).as[JsValue])
        }.map { _ =>
          default ! Quit(id)
        }

        (iteratee, enumerator)
      }

      case CannotConnect(error) => {
        // Connection error
        // A finished Iteratee sending EOF
        val iteratee = Done[JsValue, Unit]((), Input.EOF)

        // Send an error and close the socket
        val enumerator = Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))

        (iteratee, enumerator)
      }
    }
  }

  /** Websocket actor to manage incoming messages (from the user)
    * from the websocket. */
  class Websocket extends Actor {

    var members = Map.empty[String, Concurrent.Channel[JsValue]]
    var terminals = Map.empty[String, models.Terminal]

    /** Actor receive actor-message.
      * Join: create new websocket and terminal (new user joins),
      * Talk: pass received message to Communication object,
      * Quit: destroy websocket and terminal (user quits). */
    def receive = {


      case Join(id, path) => {

        // Create an Enumerator to write to this socket
//        val channel = Enumerator[JsValue]()

        val (enumerator, channel) = Concurrent.broadcast[JsValue]

        val terminal = new models.Terminal
        terminal.setWebsocket(channel)
        terminal.deactivateIfPublic(id)
        if (!terminal.publicUser)
          terminal.getSshLoginData(id)
        terminal.start

        if (members.contains(id)) {
          sender ! CannotConnect("This username is already used")
        } else {
          members = members + (id -> channel)
          terminals = terminals + (id -> terminal)
          sender ! Connected(enumerator)

          println(id + " connected!")

          var msg = JsObject(Seq(
            "type" -> JsString("editor"),
            "command" -> JsString("load"),
            "text" -> JsString("Happy Coding!"))
          ).as[JsValue]

          Websocket.send(id, msg)
          Project.join(id, path)
        }
      }

      case Send(id, text) => {
        for (m <- members.get(id)) {
          m.push(text)
        }
      }

      case Talk(id, text) => {
        Communication.commandHandling(text, terminals.getOrElse(id, null), id)
      }

      case Quit(id) => {
        for (m <- members.get(id)) {
          m.end()
        }
        members = members - id

        terminals.getOrElse(id, null).close
        terminals = terminals - id

        Project.leave(id)

        println(id + " disconnected!")
        System.gc()
      }
    }
  }

  case class Join(username: String, projectpath: String)

  case class Quit(username: String)

  case class Talk(username: String, text: JsValue)

  case class Send(username: String, text: JsValue)

  case class Connected(enumerator: Enumerator[JsValue])

  case class CannotConnect(msg: String)

}
