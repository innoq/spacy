import { fillTemplate } from "../util/template";

export class UserNotifications extends HTMLElement {
  connectedCallback() {
    if (!this.status || !this.listenFor) {
      return;
    }

    this.listenFor.forEach(fact => document.body.addEventListener(fact, this.addNotification.bind(this)));
  }

  addNotification(ev) {
    const session = ev.detail["spacy.domain/session"];
    const notification = this.newNotification(ev.type);

    this.status.innerHTML = '';

    if (!session || !this.notifyFor(ev.detail) || !notification) {
      return; // Add no notifications for things we don't understand
    }

    fillTemplate(notification, "title", session["spacy.domain/title"]);
    fillTemplate(notification, "sponsor", ev.detail["spacy.domain/sponsor"]);
    fillTemplate(notification, "room", ev.detail["spacy.domain/room"]);
    fillTemplate(notification, "time", ev.detail["spacy.domain/time"]);

    this.status.appendChild(notification);
  }

  get status() {
    return this.querySelector("small");
  }

  get currentUser() {
    return this.getAttribute("current-user");
  }

  get listenFor() {
    let nodes = this.querySelectorAll("[data-fact]");
    return [...nodes].map(el => el.getAttribute("data-fact"));
  }

  notifyFor(fact) {
    if (this.hasAttribute("notify-all")) {
      return true;
    }
    if (this.hasAttribute("notify-scheduled")) {
      return fact["spacy.domain/room"] && fact["spacy.domain/time"];
    }
    return fact["spacy.domain/sponsor"] === this.currentUser;
  }

  newNotification(fact) {
    const template = this.querySelector(`template[data-fact="${fact}"]`);
    if (!template) {
      return;
    }
    return template.content.cloneNode(true);
  }
}
