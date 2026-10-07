(() => {
  const savedRecipient = document.querySelector("[data-saved-recipient]");
  const recipientInput = document.getElementById("recipient");
  if (savedRecipient && recipientInput) {
    savedRecipient.addEventListener("change", () => {
      if (savedRecipient.value) recipientInput.value = savedRecipient.value;
    });
  }

  const sourceAccount = document.querySelector("[data-currency-select]");
  const currencyLabel = document.querySelector("[data-currency-label]");
  const currencySymbol = document.querySelector("[data-currency-symbol]");
  if (sourceAccount && currencyLabel && currencySymbol) {
    const updateCurrency = () => {
      const currency = sourceAccount.selectedOptions[0]?.dataset.currency || "PHP";
      currencyLabel.textContent = `(${currency})`;
      currencySymbol.textContent = currency === "USD" ? "$" : "₱";
    };
    sourceAccount.addEventListener("change", updateCurrency);
    updateCurrency();
  }

  document.querySelectorAll("[data-copy-value]").forEach((button) => {
    button.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(button.dataset.copyValue);
        button.textContent = "Copied";
        window.setTimeout(() => { button.textContent = "Copy account number"; }, 1800);
      } catch {
        button.textContent = "Select the number to copy";
        window.setTimeout(() => { button.textContent = "Copy account number"; }, 2200);
      }
    });
  });

})();
