export class WaitingQueue extends HTMLElement {
  connectedCallback() {
    if (!this.form || !this.list) {
      return;
    }

    this.form.addEventListener("submit", this.submitForm.bind(this));
    document.body.addEventListener("session-scheduled", this.addQueuedSession.bind(this));
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
    const element = this.newSession();
    element.querySelector("[id]").id = session.id;
    element.querySelector("[data-slot=title]").textContent = session.title;
    element.querySelector("[data-slot=sponsor]").textContent = session.sponsor;
    element.querySelector("[data-slot=description]").textContent = session.description;

    this.list.appendChild(element);
  }

  get form() {
    return this.querySelector("form");
  }

  get list() {
    return this.querySelector("ol");
  }

  newSession() {
    return this.querySelector("template").content.querySelector("*").cloneNode(true);
  }
}
