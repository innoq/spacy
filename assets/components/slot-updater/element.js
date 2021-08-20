import { createSession, currentUser } from '../session';
import { removeNode } from "uitil/dom";
import { firstInQueue } from '../waiting-queue/element';

export class SlotUpdater extends HTMLElement {
  connectedCallback() {
    document.body.addEventListener("spacy.domain/session-scheduled", this.addScheduledSession.bind(this));
    document.body.addEventListener("spacy.domain/session-deleted", this.removeDeletedSession.bind(this));
    document.body.addEventListener("spacy.domain/session-moved", this.moveSession.bind(this));
    document.body.addEventListener("spacy.ui/up-next", this.upNext.bind(this));
  }

  upNext(ev) {
    if (ev.detail === "spacy.ui/nobody-in-queue") {
      this.clearAction();
    } else {
      this.appendAction(ev.detail);
    }
  }

  addScheduledSession(ev) {
    this.clearAction();

    const room = ev.detail["spacy.domain/room"];
    const time = ev.detail["spacy.domain/time"];
    if (room !== this.room || time !== this.time) {
      return;
    }

    const sponsor = ev.detail["spacy.domain/sponsor"];
    const session = ev.detail["spacy.domain/session"];

    const el = createSession(sponsor, session, { actions: ["delete-session", "move-session"] });
    this.appendChild(el);
  }

  removeDeletedSession(ev) {
    const session = ev.detail["spacy.domain/session"];
    const id = session["spacy.domain/id"];

    if (this.session && this.session.getAttribute("data-id") === id) {
      removeNode(this.session);

      this.appendAction(firstInQueue());
    }
  }

  moveSession(ev) {
    this.deleteSession(ev);
    this.scheduleSession(ev);

    this.appendAction(firstInQueue());
  }

  /**
   * Appends the "choose slot" action only when it needs to:
   * If the currentUser() doesn't match the sponsor of the session which is up next,
   * or if the slot is already filled with something (either a session, or already
   * filled with an action), then this is a no-op.
  */
  appendAction(fact) {
    if (!fact || this.hasSessionOrAction() || currentUser() !== fact["spacy.domain/sponsor"]) {
      return;
    }

    const session = fact["spacy.domain/session"];

    const el = this.actionTemplate();
    const idField = el.querySelector("[name=id]");
    idField.value = session["spacy.domain/id"];

    this.appendChild(el);
  }

  clearAction() {
    if (this.action) {
      removeNode(this.action);
    }
  }

  hasSessionOrAction() {
    return this.session || this.action;
  }

  get action() {
    return this.querySelector("slot-updater > [data-command]");
  }

  get session() {
    return this.querySelector(".session");
  }

  get time() {
    return this.getAttribute("data-time");
  }

  get room() {
    return this.getAttribute("data-room");
  }

  actionTemplate() {
    return this.querySelector("template").content
      .querySelector("*") // Find first true HTML node which will be our form markup
      .cloneNode(true);
  }
}
