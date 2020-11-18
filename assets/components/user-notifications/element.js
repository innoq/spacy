export class UserNotifications extends HTMLElement {
  connectedCallback() {
    if (!this.entries) {
      return;
    }
    this.hidden = false;

    document.body.addEventListener("spacy.domain/session-suggested", this.addNotification.bind(this));
  }

  addNotification(ev) {
    const sponsor = ev.detail["spacy.domain/sponsor"];
    const session = ev.detail["spacy.domain/session"];
    const notification = this.newNotification(ev.type);

    if (!session || sponsor !== this.currentUser || !notification) {
      return; // Add no notifications for things we don't understand
    }

    notification.querySelector("[data-slot=title]").textContent = session["spacy.domain/title"];
    notification.querySelector("[data-slot=at").textContent = new Date(); // TODO: use Crux timestamp, but deal with timezones

    this.entries.appendChild(notification);
  }

  get entries() {
    return this.querySelector("ul");
  }

  get currentUser() {
    return this.getAttribute("current-user");
  }

  newNotification(fact) {
    const template = this.querySelector(`template[data-template="${fact}"]`);
    if (!template) {
      return;
    }
    return template.content
      .querySelector("*") // Find first true HTML node which will be our session markup
      .cloneNode(true);
  }
}
