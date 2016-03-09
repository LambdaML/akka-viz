package akkaviz.frontend.components

import akkaviz.frontend.ActorRepository.ActorState
import akkaviz.protocol
import org.scalajs.dom.{Element => domElement, _}
import rx.Var

import scalatags.JsDom.all._

trait ClosableTab extends Tab {
  def onClose(): Unit = {
    tab.parentNode.removeChild(tab)
    tabBody.parentNode.removeChild(tabBody)
  }

  override def attach(tabbedPane: domElement): Unit = {
    super.attach(tabbedPane)
    tab.appendChild(a(cls := "glyphicon glyphicon-remove", href := "#", float.left, onclick := onClose _).render)
  }
}

trait Tab extends Component {
  def name: String

  def tabId: String

  lazy val activateA = a(href := s"#$tabId", "data-toggle".attr := "tab", s"$name", float.left).render
  lazy val tab = li(activateA).render

  lazy val tabBody = div(`class` := "tab-pane panel panel-default ", id := s"$tabId").render

  override def attach(tabbedPane: domElement): Unit = {
    tabbedPane.querySelector("ul.nav-tabs").appendChild(tab)
    tabbedPane.querySelector("div.tab-content").appendChild(tabBody)
    activateA.click()
  }

}

class ActorStateTab(actorState: Var[ActorState], upstreamSend: protocol.ApiClientMessage => Unit) extends ClosableTab {

  import akkaviz.frontend.PrettyJson._
  import ActorStateTab._

  val name = actorState.now.path
  val tabId = stateTabId(actorState.now.path)

  val stateObs = actorState.foreach(renderState(_))

  def renderState(state: ActorState) = {
    lazy val fsmDiv = div(cls := s"fsm-graph").render
    lazy val fsmGraph = new FsmGraph(fsmDiv)

    val rendered = div(
      cls := "panel-body",
      refreshButton(actorState.now.path),
      fsmDiv,
      div(strong("Class: "), state.className.getOrElse[String]("Unknown class")),
      div(strong("Is dead: "), state.isDead.toString),
      div(strong("Internal state: "), pre(state.internalState.map(prettyPrintJson).getOrElse[String]("Internal state unknown"))),
      div(strong("Is FSM: "), state.fsmState.isDefined.toString),
      state.fsmState.map[Frag] {
        fsm =>
          Seq(
            div(strong("FSM State: "), pre(prettyPrintJson(fsm.currentState))),
            div(strong("FSM Data: "), pre(prettyPrintJson(fsm.currentData)))
          )
      }.getOrElse(()),
      div(strong("Mailbox size: "), state.mailboxSize.map(_.toString).getOrElse[String]("Unknown")),
      div(strong("Last updated: "), state.lastUpdatedAt.toISOString())
    ).render

    tabBody.innerHTML = ""
    tabBody.appendChild(rendered)
    fsmGraph.displayFsm(state.fsmTransitions)
  }

  override def onClose() = {
    super.onClose()
    stateObs.kill()
  }

  private[this] def refreshButton(actorRef: String) =
    a(cls := "btn btn-default", href := "#", role := "button", float.right,
      span(
        `class` := "imgbtn glyphicon glyphicon-refresh", " "
      ),
      onclick := { () =>
        upstreamSend(protocol.RefreshInternalState(actorRef))
      },
      "Refresh state")

}

object ActorStateTab {
  def stateTabId(path: String): String = {
    s"actor-state-${path.replaceAll("[\\/|\\.|\\\\|\\$]", "-").filterNot(_ == ':')}"
  }
}

