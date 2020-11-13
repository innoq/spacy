import { createSession } from "../session";

export class WaitingQueue extends HTMLElement {
  connectedCallback() {
    if (!this.form || !this.list) {
      return;
    }

    this.form.addEventListener("submit", this.submitForm.bind(this));
    document.body.addEventListener("session-suggested", this.addQueuedSession.bind(this));
  }

  submitForm(ev) {
    this.form.closest("hijax-form").submit()
      .then(response => {
        console.log(response.text())
      });

    ev.preventDefault();
  }

  addQueuedSession(ev) {
    const session = ev.detail["spacy.app/session"];
    if (!session) {
      console.error("Could not find session to add to queue.");
      return;
    }
    const element = createSession(session.sponsor, session);

    this.list.appendChild(element);
  }

  get form() {
    return this.querySelector("form");
  }

  get list() {
    return this.querySelector("ol");
  }

  newSession() {
    return document.getElementById("session-template").content
      .querySelector("*") // Find first true HTML node which will be our session markup
      .cloneNode(true);
  }
}
