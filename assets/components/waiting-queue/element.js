import { createSession, extractSession } from "../session";
import { removeNode } from "uitil/dom";

export class WaitingQueue extends HTMLElement {
  connectedCallback() {
    if (!this.list) {
      return;
    }

    document.body.addEventListener("spacy.domain/session-suggested", this.addQueuedSession.bind(this));
    document.body.addEventListener("spacy.domain/session-scheduled", this.removedQueuedSession.bind(this));
    document.body.addEventListener("spacy.domain/session-deleted", this.removedQueuedSession.bind(this));
  }

  addQueuedSession(ev) {
    const sponsor = ev.detail["spacy.domain/sponsor"];
    const session = ev.detail["spacy.domain/session"];
    if (!sponsor || !session) {
      console.error("Could not find session to add to queue.");
      return;
    }
    const element = createSession(sponsor, session, "li");

    this.list.appendChild(element);

    if (this.list.children.length === 1) {
      this.fireUpNextEvent(ev.detail);
    }
  }

  removedQueuedSession(ev) {
    const session = ev.detail["spacy.domain/session"];
    const entryInList = session && this.sessionEntry(session["spacy.domain/id"]);

    if (!entryInList) {
      return; // Do nothing when we do not have the session in our list.
    }

    removeNode(entryInList);

    this.fireUpNextEvent(this.list.children.length ? extractSession(this.list.children[0]) : "spacy.ui/nobody-in-queue");
  }

  fireUpNextEvent(detail) {
    this.dispatchEvent(new CustomEvent("spacy.ui/up-next", { detail, bubbles: true }));
  }

  get list() {
    return this.querySelector("ol");
  }

  sessionEntry(id) {
    const session = this.querySelector(`[data-id="${id}"]`);
    return session && session.closest("li");
  }
}
