const state = {
  balance: 2800,
  transactions: [
    { type: "in", icon: "↓", title: "Cash in", detail: "Today, 9:42 AM · Salary", amount: 2500 },
    { type: "out", icon: "↗", title: "Transfer to Maria Santos", detail: "Yesterday, 4:18 PM · Personal", amount: -500 },
    { type: "in", icon: "↓", title: "Cash in", detail: "Sep 08, 2:05 PM · Allowance", amount: 2000 },
    { type: "out", icon: "↗", title: "Transfer to 09990000002", detail: "Sep 07, 11:36 AM · Personal", amount: -700 }
  ],
  filter: "all",
  query: ""
};

let toastTimer;

function money(value) {
  return "PHP " + value.toLocaleString("en-PH", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function formatAmount(value) {
  return (value >= 0 ? "+ " : "− ") + money(Math.abs(value));
}

function updateBalance() {
  document.querySelectorAll("[data-balance]").forEach(function (element) {
    element.textContent = money(state.balance);
  });
}

function renderRows(target, rows) {
  target.innerHTML = rows.map(function (row) {
    return (
      '<article class="activity-row">' +
      '<span class="activity-icon ' + (row.type === "in" ? "mint" : "coral") + '" aria-hidden="true">' + row.icon + "</span>" +
      '<span class="activity-copy"><strong>' + row.title + "</strong><small>" + row.detail + "</small></span>" +
      '<span class="activity-amount ' + row.type + '">' + formatAmount(row.amount) + "</span>" +
      '<span class="activity-status">Completed</span>' +
      "</article>"
    );
  }).join("");
}

function visibleTransactions() {
  return state.transactions.filter(function (transaction) {
    const matchesFilter = state.filter === "all" || transaction.type === state.filter;
    const haystack = (transaction.title + " " + transaction.detail).toLowerCase();
    const matchesQuery = !state.query || haystack.indexOf(state.query.toLowerCase()) !== -1;
    return matchesFilter && matchesQuery;
  });
}

function renderTransactions() {
  const rows = visibleTransactions();
  document.querySelectorAll("[data-activity-list]").forEach(function (target) {
    renderRows(target, rows.slice(0, 3));
  });
  document.querySelectorAll("[data-full-activity-list]").forEach(function (target) {
    renderRows(target, rows);
  });
  document.querySelectorAll("[data-transaction-count]").forEach(function (element) {
    element.textContent = String(state.transactions.length + 19);
  });
}

function setView(viewId) {
  document.querySelectorAll("[data-view]").forEach(function (view) {
    view.classList.toggle("is-visible", view.dataset.view === viewId);
  });
  document.querySelectorAll("[data-nav]").forEach(function (link) {
    const active = link.dataset.nav === viewId;
    link.classList.toggle("is-active", active);
    if (active) {
      link.setAttribute("aria-current", "page");
    } else {
      link.removeAttribute("aria-current");
    }
  });
  document.querySelector(".sidebar")?.classList.remove("is-open");
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function showToast(message, isError) {
  const toast = document.querySelector("[data-toast]");
  if (!toast) return;
  toast.textContent = message;
  toast.classList.toggle("is-error", Boolean(isError));
  toast.classList.add("is-visible");
  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(function () {
    toast.classList.remove("is-visible");
  }, 3200);
}

function openModal(name) {
  const dialog = document.querySelector('[data-modal="' + name + '"]');
  if (!dialog) return;
  if (typeof dialog.showModal === "function") {
    dialog.showModal();
  } else {
    dialog.setAttribute("open", "");
  }
  const firstInput = dialog.querySelector("input, select");
  if (firstInput) firstInput.focus();
}

function closeModal(button) {
  const dialog = button.closest("dialog");
  if (dialog && typeof dialog.close === "function") {
    dialog.close();
  } else if (dialog) {
    dialog.removeAttribute("open");
  }
}

function bindModalForms() {
  document.querySelectorAll("[data-modal-form]").forEach(function (form) {
    form.addEventListener("submit", function (event) {
      event.preventDefault();
      const amountInput = form.querySelector("[name=amount]");
      const amount = Number(amountInput.value);
      if (!amount || amount <= 0) {
        showToast("Enter an amount greater than zero.", true);
        return;
      }

      const today = "Today, just now · Demo activity";
      if (form.dataset.modalForm === "cash-in") {
        state.balance += amount;
        state.transactions.unshift({
          type: "in",
          icon: "↓",
          title: "Cash in",
          detail: today,
          amount: amount
        });
        showToast(money(amount) + " added to your balance.");
      } else {
        if (amount > state.balance) {
          showToast("That transfer is higher than your available balance.", true);
          return;
        }
        const recipient = form.querySelector("[name=recipient]").value.trim() || "Demo recipient";
        state.balance -= amount;
        state.transactions.unshift({
          type: "out",
          icon: "↗",
          title: "Transfer to " + recipient,
          detail: today,
          amount: -amount
        });
        showToast(money(amount) + " transfer queued for review.");
      }

      updateBalance();
      renderTransactions();
      form.reset();
      closeModal(form);
    });
  });
}

document.addEventListener("DOMContentLoaded", function () {
  updateBalance();
  renderTransactions();
  bindModalForms();

  document.querySelectorAll("[data-nav]").forEach(function (link) {
    link.addEventListener("click", function () {
      setView(link.dataset.nav);
    });
  });

  document.querySelectorAll("[data-open-modal]").forEach(function (button) {
    button.addEventListener("click", function () {
      openModal(button.dataset.openModal);
    });
  });

  document.querySelectorAll("[data-close-modal]").forEach(function (button) {
    button.addEventListener("click", function () {
      closeModal(button);
    });
  });

  document.querySelectorAll("dialog").forEach(function (dialog) {
    dialog.addEventListener("click", function (event) {
      if (event.target === dialog) closeModal(dialog.querySelector("[data-close-modal]"));
    });
  });

  document.querySelectorAll("[data-filter]").forEach(function (button) {
    button.addEventListener("click", function () {
      state.filter = button.dataset.filter;
      document.querySelectorAll("[data-filter]").forEach(function (item) {
        item.classList.toggle("is-active", item === button);
      });
      renderTransactions();
    });
  });

  document.querySelectorAll("[data-search]").forEach(function (input) {
    input.addEventListener("input", function () {
      state.query = input.value.trim();
      renderTransactions();
    });
  });

  document.querySelectorAll("[data-balance-toggle]").forEach(function (button) {
    button.addEventListener("click", function () {
      const isHidden = document.body.classList.toggle("balances-hidden");
      button.setAttribute("aria-pressed", String(isHidden));
      button.querySelector("span").textContent = isHidden ? "Show balance" : "Hide balance";
    });
  });

  document.querySelector("[data-mobile-menu]")?.addEventListener("click", function () {
    document.querySelector(".sidebar")?.classList.toggle("is-open");
  });

  document.querySelector("[data-notification]")?.addEventListener("click", function () {
    showToast("You are all caught up.");
  });
});
