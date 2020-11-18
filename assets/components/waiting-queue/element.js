import { createSession } from "../session";
import { removeNode } from "uitil/dom";

export class WaitingQueue extends HTMLElement {
  connectedCallback() {
    if (!this.list) {
      return;
    }

    document.body.addEventListener("spacy.domain/session-suggested", this.addQueuedSession.bind(this));
    document.body.addEventListener("spacy.domain/session-scheduled", this.removedQueuedSession.bind(this));
  }

  addQueuedSession(ev) {
    const sponsor = ev.detail["spacy.domain/sponsor"];
    const session = ev.detail["spacy.domain/session"];
    if (!sponsor || !session) {
      console.error("Could not find session to add to queue.");
      return;
    }
    const element = createSession(sponsor, session);

    this.list.appendChild(element);
  }

  removedQueuedSession(ev) {
    const session = ev.detail["spacy.domain/session"];
    const entryInList = session && this.sessionEntry(session["spacy.domain/id"]);

    if (!entryInList) {
      return; // Do nothing when we do not have the session in our list.
    }

    removeNode(entryInList);
  }

  get list() {
    return this.querySelector("ol");
  }

  sessionEntry(id) {
    const session = this.querySelector(`[data-id="${id}"]`);
    return session && session.closest("li");
  }
}
