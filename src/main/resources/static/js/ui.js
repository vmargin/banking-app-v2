(() => {
  const menus = [...document.querySelectorAll(".mobile-menu")];
  function closeMenu(menu, restoreFocus = false) {
    if (!menu.open) return;
    menu.open = false;
    if (restoreFocus) menu.querySelector("summary").focus();
  }
  menus.forEach((menu) => {
    const close = menu.querySelector("[data-menu-close]");
    close.hidden = false;
    close.addEventListener("click", () => closeMenu(menu, true));
    menu.addEventListener("focusout", (event) => {
      if (event.relatedTarget && !menu.contains(event.relatedTarget)) closeMenu(menu);
    });
  });
  document.addEventListener("pointerdown", (event) => {
    menus.forEach((menu) => { if (!menu.contains(event.target)) closeMenu(menu); });
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") menus.forEach((menu) => closeMenu(menu, true));
    if (event.key !== "/" || event.ctrlKey || event.metaKey || event.altKey) return;
    if (event.target.closest("input, textarea, select, [contenteditable]")) return;
    const search = document.getElementById("global-search");
    if (search) { event.preventDefault(); search.focus(); }
  });

  document.querySelectorAll("[data-pin-toggle]").forEach((button) => {
    const input = document.getElementById(button.getAttribute("aria-controls"));
    if (!input) return;
    button.hidden = false;
    button.addEventListener("click", () => {
      const show = input.type === "password";
      input.type = show ? "text" : "password";
      button.setAttribute("aria-pressed", String(show));
      button.textContent = show ? "Hide PIN" : "Show PIN";
    });
  });

  const formStates = [];
  document.querySelectorAll("form").forEach((form, formIndex) => {
    const fields = [...form.elements].filter((field) => field.willValidate && field.tagName !== "BUTTON");
    const state = { form, busy: false, button: null, buttonText: "" };
    formStates.push(state);
    if (fields.length) form.noValidate = true;
    let summary;
    const errors = new Map();

    function clearError(field) {
      const error = errors.get(field);
      if (!error) return;
      const descriptions = (field.getAttribute("aria-describedby") || "").split(/\s+/).filter((id) => id && id !== error.id);
      if (descriptions.length) field.setAttribute("aria-describedby", descriptions.join(" "));
      else field.removeAttribute("aria-describedby");
      field.removeAttribute("aria-invalid");
      error.remove();
      errors.delete(field);
    }

    function showError(field, fieldIndex) {
      if (!field.id) field.id = `form-${formIndex}-field-${fieldIndex}`;
      let error = errors.get(field);
      if (!error) {
        error = document.createElement("p");
        error.id = `${field.id}-error`;
        error.className = "field-error";
        (field.closest(".money-input, .pin-field") || field).after(error);
        errors.set(field, error);
        field.setAttribute("aria-describedby", [field.getAttribute("aria-describedby"), error.id].filter(Boolean).join(" "));
      }
      error.textContent = field.validationMessage;
      field.setAttribute("aria-invalid", "true");
    }

    function updateSummary() {
      if (!summary) return;
      if (!errors.size) { summary.remove(); summary = null; return; }
      const list = summary.querySelector("ul");
      list.replaceChildren();
      errors.forEach((error, field) => {
        const item = document.createElement("li");
        const link = document.createElement("a");
        const label = field.labels?.[0]?.textContent.trim() || field.name;
        link.href = `#${field.id}`;
        link.textContent = `${label}: ${error.textContent}`;
        link.addEventListener("click", (event) => { event.preventDefault(); field.focus(); });
        item.append(link);
        list.append(item);
      });
    }

    fields.forEach((field, fieldIndex) => {
      const recheck = () => {
        if (!errors.has(field)) return;
        if (field.validity.valid) clearError(field);
        else showError(field, fieldIndex);
        updateSummary();
      };
      field.addEventListener("input", recheck);
      field.addEventListener("change", recheck);
    });

    form.addEventListener("submit", (event) => {
      if (state.busy) { event.preventDefault(); return; }
      fields.forEach((field, index) => {
        if (field.validity.valid) clearError(field);
        else showError(field, index);
      });
      if (errors.size) {
        event.preventDefault();
        if (!summary) {
          summary = document.createElement("div");
          summary.className = "alert error form-error-summary";
          summary.setAttribute("role", "alert");
          summary.tabIndex = -1;
          const heading = document.createElement("h3");
          heading.id = `form-${formIndex}-error-title`;
          heading.textContent = "Check these details";
          summary.setAttribute("aria-labelledby", heading.id);
          summary.append(heading, document.createElement("ul"));
          form.prepend(summary);
        }
        updateSummary();
        summary.focus();
        return;
      }
      if (form.method.toLowerCase() !== "post") return;
      state.busy = true;
      form.setAttribute("aria-busy", "true");
      const button = event.submitter;
      // Named submit buttons must stay enabled so their value reaches the server.
      if (button && !button.name) {
        state.button = button;
        state.buttonText = button.textContent;
        button.disabled = true;
        button.textContent = "Submitting…";
      }
    });
  });

  window.addEventListener("pageshow", () => {
    formStates.forEach((state) => {
      state.busy = false;
      state.form.removeAttribute("aria-busy");
      if (state.button) {
        state.button.disabled = false;
        state.button.textContent = state.buttonText;
        state.button = null;
      }
    });
  });

  const serverError = document.querySelector(".alert.error[role='alert']");
  if (serverError) { serverError.tabIndex = -1; serverError.focus(); }
})();
