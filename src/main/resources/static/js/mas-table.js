const root = document.querySelector(".table-shell");
const gameId = root.dataset.gameId;
const playerId = root.dataset.playerId;

const els = {
    startGame: document.querySelector("#startGame"),
    startRoundTwo: document.querySelector("#startRoundTwo"),
    lobbyLink: document.querySelector("#lobbyLink"),
    alert: document.querySelector("#alert"),
    players: document.querySelector("#players"),
    status: document.querySelector("#status"),
    deckCount: document.querySelector("#deckCount"),
    trumpSuit: document.querySelector("#trumpSuit"),
    turnTitle: document.querySelector("#turnTitle"),
    pendingOffer: document.querySelector("#pendingOffer"),
    loserTitleBox: document.querySelector("#loserTitleBox"),
    roundTwoTable: document.querySelector("#roundTwoTable"),
    actions: document.querySelector("#actions"),
    latestEvent: document.querySelector("#latestEvent"),
    hand: document.querySelector("#hand")
};

let state = null;

function connect() {
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const socket = new WebSocket(`${protocol}://${window.location.host}/ws/mas/${gameId}?playerId=${playerId}`);
    socket.addEventListener("message", event => {
        state = JSON.parse(event.data);
        render();
    });
    socket.addEventListener("close", () => {
        showAlert("Anslutningen tappades. Försöker igen strax.");
        window.setTimeout(connect, 1200);
    });
}

async function post(path, params = {}) {
    hideAlert();
    const body = new URLSearchParams({ playerId, ...params });
    const response = await fetch(path, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body
    });
    if (!response.ok) {
        const error = await response.json().catch(() => ({ message: "Draget kunde inte genomföras." }));
        showAlert(error.message);
    }
}

function render() {
    renderHeader();
    renderPlayers();
    renderHand();
    renderPendingOffer();
    renderLoserTitleBox();
    renderRoundTwoTable();
    renderActions();
    renderLatestEvent();
}

function renderHeader() {
    els.startGame.hidden = !state.canStart;
    els.startRoundTwo.hidden = !state.canStartRoundTwo;
    els.lobbyLink.hidden = !state.gameFinished;
    els.status.textContent = state.status;
    els.deckCount.textContent = state.deckCount;
    els.trumpSuit.textContent = state.trumpSuit || "-";
    if (state.roundOneFinished) {
        els.turnTitle.textContent = `Omgång 1 är klar. Trumf: ${state.trumpSuit || "-"}`;
    } else if (state.gameFinished) {
        els.turnTitle.textContent = `${state.loserName || "Sista spelaren"} är dagens ${state.loserTitle || "förlorare"}`;
    } else if (state.roundTwo && state.activePlayerName) {
        els.turnTitle.textContent = `${state.activePlayerName} spelar i omgång 2`;
    } else if (state.pendingOffer) {
        els.turnTitle.textContent = `${state.pendingOffer.receiverName} svarar på skick från ${state.pendingOffer.senderName}`;
    } else if (state.activePlayerName) {
        els.turnTitle.textContent = `${state.activePlayerName} är aktiv spelare`;
    } else {
        els.turnTitle.textContent = "Väntar på start";
    }
}

function renderLoserTitleBox() {
    if (!state.waiting) {
        els.loserTitleBox.replaceChildren();
        return;
    }

    const box = document.createElement("section");
    box.className = "waiting-tool";

    const heading = document.createElement("div");
    heading.innerHTML = `
        <p class="eyebrow">Förlorartitel</p>
        <strong>${state.loserTitle ? `Dagens ${escapeHtml(state.loserTitle)}` : "Inget valt ännu"}</strong>
    `;

    const form = document.createElement("form");
    form.className = "suggestion-form";
    form.innerHTML = `
        <input name="title" maxlength="40" required placeholder="t.ex. sopa">
        <button class="button" type="submit">Föreslå</button>
    `;
    form.addEventListener("submit", event => {
        event.preventDefault();
        const input = form.elements.title;
        post(`/api/mas/${gameId}/loser-title/suggest`, { title: input.value });
        input.value = "";
    });

    const list = document.createElement("div");
    list.className = "suggestions";
    state.loserTitleSuggestions.forEach(suggestion => {
        const row = document.createElement("div");
        row.className = "suggestion";
        const selected = state.loserTitle && state.loserTitle.toLowerCase() === suggestion.title.toLowerCase();
        row.innerHTML = `
            <span>dagens ${escapeHtml(suggestion.title)}</span>
            <small>${escapeHtml(suggestion.playerName)}</small>
        `;
        if (state.youAreHost) {
            const button = actionButton(selected ? "Vald" : "Välj", () => {
                post(`/api/mas/${gameId}/loser-title/select`, { suggestionId: suggestion.id });
            }, selected);
            row.append(button);
        } else if (selected) {
            const pill = document.createElement("span");
            pill.className = "pill";
            pill.textContent = "Vald";
            row.append(pill);
        }
        list.append(row);
    });

    box.append(heading, form, list);
    els.loserTitleBox.replaceChildren(box);
}

function renderPlayers() {
    els.players.replaceChildren(...state.players.map(player => {
        const div = document.createElement("div");
        div.className = `player${player.active ? " is-active" : ""}`;
        div.innerHTML = `
            <strong>${escapeHtml(player.name)}${player.you ? " (du)" : ""}</strong>
            <span class="pill">${player.handCount} kort</span>
            <small>${player.wonCardCount} kort i stick</small>
        `;
        return div;
    }));
}

function renderHand() {
    if (state.hand.length === 0) {
        els.hand.innerHTML = `<p class="empty">Inga kort på handen.</p>`;
        return;
    }
    els.hand.replaceChildren(...state.hand.map(card => {
        const button = document.createElement("button");
        button.className = "card-button";
        button.type = "button";
        button.title = card.name;
        button.disabled = !canPlayCard(card);
        button.append(cardImage(card));
        button.addEventListener("click", () => playCard(card));
        return button;
    }));
}

function renderPendingOffer() {
    if (!state.pendingOffer || state.roundTwo) {
        els.pendingOffer.replaceChildren();
        return;
    }
    const label = document.createElement("div");
    label.innerHTML = `
        <p class="eyebrow">Skickat kort</p>
        <strong>${escapeHtml(state.pendingOffer.senderName)} → ${escapeHtml(state.pendingOffer.receiverName)}</strong>
    `;
    const card = cardElement(state.pendingOffer.sentCard);
    els.pendingOffer.replaceChildren(label, card);
}

function renderRoundTwoTable() {
    if (!state.roundTwo) {
        els.roundTwoTable.replaceChildren();
        return;
    }
    const header = document.createElement("div");
    header.innerHTML = `
        <p class="eyebrow">Aktuellt stick</p>
        <strong>${state.roundTwoTable.length} av ${state.roundTwoTrickSize} kort</strong>
    `;
    const cards = document.createElement("div");
    cards.className = "event-cards";
    state.roundTwoTable.forEach(play => {
        const wrapper = document.createElement("div");
        wrapper.className = "played-card";
        const name = document.createElement("small");
        name.textContent = play.text;
        wrapper.append(cardElement(play.sentCard), name);
        cards.append(wrapper);
    });
    els.roundTwoTable.replaceChildren(header, cards);
}

function renderActions() {
    const actions = [];
    if (state.youAreActive && !state.roundTwo) {
        actions.push(actionButton("Dra från högen och skicka", () => post(`/api/mas/${gameId}/send-from-deck`), !state.canSendFromDeck));
    }
    if (state.youAreReceiver && receiverHasNoSuit()) {
        actions.push(actionButton("Ta upp kortet", () => post(`/api/mas/${gameId}/pickup`), false));
    }
    els.actions.replaceChildren(...actions);
}

function renderLatestEvent() {
    const latest = state.events[0];
    if (!latest || state.pendingOffer) {
        els.latestEvent.hidden = true;
        els.latestEvent.textContent = "";
        return;
    }
    els.latestEvent.hidden = false;
    els.latestEvent.textContent = latest.text;
}

function canPlayCard(card) {
    if (state.roundTwo) {
        return state.youAreActive;
    }
    if (state.youAreActive) {
        return true;
    }
    if (!state.youAreReceiver || !state.pendingOffer) {
        return false;
    }
    return card.suit === state.pendingOffer.sentCard.suit;
}

function playCard(card) {
    if (state.roundTwo) {
        post(`/api/mas/${gameId}/round-two/play`, { cardCode: card.code });
        return;
    }
    if (state.youAreActive) {
        post(`/api/mas/${gameId}/send`, { cardCode: card.code });
        return;
    }
    if (state.youAreReceiver) {
        post(`/api/mas/${gameId}/respond`, { cardCode: card.code });
    }
}

function receiverHasNoSuit() {
    return state.pendingOffer && state.hand.every(card => card.suit !== state.pendingOffer.sentCard.suit);
}

function actionButton(label, onClick, disabled) {
    const button = document.createElement("button");
    button.className = "button";
    button.type = "button";
    button.textContent = label;
    button.disabled = disabled;
    button.addEventListener("click", onClick);
    return button;
}

function cardElement(card) {
    const div = document.createElement("div");
    div.className = "card";
    div.title = card.name;
    div.append(cardImage(card));
    return div;
}

function cardImage(card) {
    const img = document.createElement("img");
    img.src = card.imagePath;
    img.alt = card.name;
    return img;
}

function showAlert(message) {
    els.alert.hidden = false;
    els.alert.textContent = message;
}

function hideAlert() {
    els.alert.hidden = true;
    els.alert.textContent = "";
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

els.startGame.addEventListener("click", () => post(`/api/mas/${gameId}/start`));
els.startRoundTwo.addEventListener("click", () => post(`/api/mas/${gameId}/start-round-two`));
connect();
