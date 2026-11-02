"""An inexpensive, deterministic explanation engine grounded in observed records."""

import re


def respond(message, status, positions, trades):
    text = " ".join(message.lower().split())
    result = {"reply": "", "intent": "help", "proposed_action": None, "requires_confirmation": False}
    if re.search(r"\b(stop|emergency|shut down)\b", text):
        result.update(intent="emergency_stop", proposed_action="emergency_stop", requires_confirmation=True,
                      reply="I can request an emergency stop after you confirm. It blocks new orders; existing positions are not automatically closed.")
        return result
    if re.search(r"\b(pause|halt)\b", text):
        result.update(intent="pause", proposed_action="pause", requires_confirmation=True,
                      reply="I can request a pause after you confirm. Protective position management continues in MT5; new entries are paused.")
        return result
    if any(word in text for word in ("start trading", "resume trading", "live account", "real money")):
        result.update(intent="control_help", reply="Use the Control Centre for an explicit start or resume. Live operation requires local pairing to the exact account; risk checks remain mandatory.")
        return result
    if any(word in text for word in ("connected", "connection", "robot doing", "status")):
        result["intent"] = "status"
        if status["connected"]:
            result["reply"] = f"The {status['mode']} account is connected. The robot is {status['robot_state']}. {status.get('reason') or ''}"
        else:
            result["reply"] = "I do not have a current MT5 connection. " + (status.get("reason") or "Check MT5 and the local bridge.")
        return result
    if "history" in text:
        result.update(intent="history", reply=f"There are {len(trades)} broker trade events in the currently loaded history. Open Trading Centre to review them. Submitted requests are not counted as completed trades.")
        return result
    if not status["connected"]:
        result.update(intent="unavailable", reply="Current account data is unavailable or stale. I cannot verify balances, profits, positions, or market conditions until MT5 reconnects.")
        return result
    currency = status.get("currency") or "account currency"
    if any(word in text for word in ("profit", "lose", "lost", "loss", "performance", "money", "balance", "equity")):
        field = "weekly_pnl" if "week" in text else "daily_pnl"
        period = "this week" if field == "weekly_pnl" else "today"
        if "balance" in text:
            field, period = "balance", "account balance"
        elif "equity" in text:
            field, period = "equity", "account equity"
        value = status.get(field)
        result["intent"] = "performance"
        if value is None:
            result["reply"] = "MT5 has not provided a verified value for that statistic yet."
        elif field in {"balance", "equity"}:
            result["reply"] = f"Your verified {period} is {value:,.2f} {currency}. This is a {status['mode']} account."
        else:
            result["reply"] = f"MT5 reports an equity change of {value:,.2f} {currency} {period}, including floating results. Cash movements can also affect equity change. This is a {status['mode']} account; it is not a promise of future profit."
        return result
    if "why" in text and any(word in text for word in ("buy", "sell", "trade", "bought", "sold")):
        compact = re.sub(r"[^a-z0-9]", "", text)
        selected = next((trade for trade in trades if re.sub(r"[^a-z0-9]", "", trade.get("symbol", "").lower()) in compact and trade.get("symbol")), None)
        if selected is None:
            selected = trades[0] if trades else None
        result["intent"] = "explain_trade"
        if selected:
            result["reply"] = (f"The recorded {selected.get('side', '')} event for {selected.get('symbol', 'this instrument')} "
                               f"belongs to strategy {selected.get('strategy') or 'not recorded'}. "
                               f"Recorded explanation: {selected.get('message') or 'No detailed reason was recorded.'} "
                               "That describes the recorded decision, not a guarantee that the trade will profit.")
        else:
            result["reply"] = "There is no recorded broker trade available to explain."
        return result
    if any(word in text for word in ("open", "positions", "active trades")):
        result["intent"] = "positions"
        if not positions:
            result["reply"] = "The latest MT5 snapshot contains no open positions."
        else:
            summary = "; ".join(f"{p['side']} {p['symbol']}, {p['volume']:g} lots, floating result {p['profit']:,.2f} {currency}" for p in positions[:5])
            result["reply"] = f"MT5 reports {len(positions)} open positions. {summary}."
        return result
    if any(word in text for word in ("market", "analysis", "signal")):
        result.update(intent="market", reply="The current terminal explanation is: " + (status.get("reason") or "No market-analysis explanation has been received.") + " I do not have an independent live news feed.")
        return result
    result["reply"] = "You can ask about verified profits, losses, open positions, trade history, connection status, or recorded trading decisions. I can propose a pause or emergency stop for you to confirm."
    return result
