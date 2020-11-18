import HijaxFormInternal from "hijax-form/form";

/*
The internal HijaxForm custom element prepares the form to be able to
be submitted as an ajax request, but does not actually perform the submitting
of the form. For our applicatin, we want to do both because our form submitting
should always do the same thing: maybe log an error, but otherwise ignore the
response which is returned because we are waiting for the SSE events which will
come from the server.
*/
export class HijaxForm extends HijaxFormInternal {
  connectedCallback() {
    super.connectedCallback();

    this.form.addEventListener("submit", this.submitForm.bind(this));
  }

  submitForm(ev) {
    this.submit()
      .then(response => {
        console.log(response.text())
      });

    ev.preventDefault();
  }

  get form() {
    return this.querySelector("form");
  }
}
