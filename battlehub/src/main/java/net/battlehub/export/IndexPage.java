package net.battlehub.export;

/** A small stats page next to the JSON files: written once, so a server can change or replace it. */
final class IndexPage {
	private IndexPage() {}

	static final String HTML = """
			<!doctype html>
			<html lang="en">
			<head>
			<meta charset="utf-8">
			<meta name="viewport" content="width=device-width, initial-scale=1">
			<title>BattleHub stats</title>
			<style>
			:root { --bg: #f6f7f9; --card: #ffffff; --text: #1d2330; --muted: #6b7385; --accent: #c8901e; --line: #e3e6ec; }
			@media (prefers-color-scheme: dark) {
			  :root { --bg: #12151b; --card: #1b2029; --text: #e8ebf1; --muted: #9aa3b5; --accent: #f0b43c; --line: #2a303b; }
			}
			* { box-sizing: border-box; }
			body { margin: 0; font: 15px/1.5 system-ui, sans-serif; background: var(--bg); color: var(--text); }
			header { padding: 24px 16px 8px; max-width: 1100px; margin: 0 auto; }
			h1 { margin: 0; font-size: 28px; } h1 span { color: var(--accent); }
			#status { color: var(--muted); margin-top: 4px; }
			main { max-width: 1100px; margin: 0 auto; padding: 8px 16px 40px; display: grid; gap: 16px;
			  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); }
			section { background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 14px 16px; }
			h2 { margin: 0 0 8px; font-size: 16px; }
			select { font: inherit; padding: 2px 6px; margin-left: 6px; }
			table { width: 100%; border-collapse: collapse; }
			td { padding: 4px 2px; border-top: 1px solid var(--line); }
			td.r { text-align: right; font-variant-numeric: tabular-nums; }
			td.n { color: var(--muted); width: 2.5em; }
			.empty { color: var(--muted); }
			</style>
			</head>
			<body>
			<header><h1><span>Battle</span>Hub</h1><div id="status">Loading...</div></header>
			<main>
			  <section><h2>Leaderboard <select id="board"></select></h2><table id="top"></table></section>
			  <section><h2>Ranked <select id="elo"></select></h2><table id="ranked"></table></section>
			  <section><h2>Clans</h2><table id="clans"></table></section>
			  <section><h2>Weekly tournament</h2><div id="weekly"></div></section>
			  <section><h2>Recent matches</h2><table id="matches"></table></section>
			</main>
			<script>
			const get = f => fetch(f, {cache: "no-store"}).then(r => r.json());
			const esc = s => String(s).replace(/[&<>"]/g, c => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;"}[c]));
			function rows(el, list, cells) {
			  el.innerHTML = list.length ? list.map(x => "<tr>" + cells(x).join("") + "</tr>").join("")
			    : "<tr><td class='empty'>Nothing yet</td></tr>";
			}
			const names = {kills: "Kills", wins: "Wins", deaths: "Deaths", kd: "K/D", level: "Level (XP)", prestige: "Prestige",
			  coinsEarned: "Coins earned", tournaments: "Tournaments won", weekly: "Weekly tournaments won"};
			Promise.all([get("leaderboards.json"), get("server.json"), get("clans.json"), get("matches.json"), get("tournaments.json")])
			  .then(([lb, srv, clans, matches, weekly]) => {
			    const when = new Date(srv.generated).toLocaleString();
			    document.getElementById("status").textContent = srv.online + " online, " + srv.inBattleHub + " in BattleHub"
			      + (srv.featuredMode ? " | mode of the day: " + srv.featuredMode : "") + " | updated " + when;
			    const board = document.getElementById("board");
			    board.innerHTML = Object.keys(names).map(k => "<option value='" + k + "'>" + names[k] + "</option>").join("");
			    const showTop = () => rows(document.getElementById("top"), lb.boards[board.value] || [], r => [
			      "<td class='n'>" + r.rank + "</td>", "<td>" + esc(r.name) + "</td>",
			      "<td class='r'>" + (board.value === "kd" ? (r.value / 100).toFixed(2) : r.value) + "</td>"]);
			    board.onchange = showTop; showTop();
			    const elo = document.getElementById("elo");
			    elo.innerHTML = Object.keys(lb.boards.elo).map(k => "<option>" + k + "</option>").join("");
			    const showElo = () => rows(document.getElementById("ranked"), lb.boards.elo[elo.value] || [], r => [
			      "<td class='n'>" + r.rank + "</td>", "<td>" + esc(r.name) + "</td>", "<td class='r'>" + r.value + "</td>"]);
			    elo.onchange = showElo; showElo();
			    rows(document.getElementById("clans"), clans.clans.slice(0, 15), c => [
			      "<td class='n'>" + c.rank + "</td>", "<td>[" + esc(c.tag) + "] " + esc(c.name) + "</td>",
			      "<td class='r'>" + c.points + " pts</td>"]);
			    rows(document.getElementById("matches"), matches.matches.slice(0, 15), m => [
			      "<td>" + esc(m.mode) + (m.ranked ? " (ranked)" : "") + "</td>",
			      "<td>" + esc(m.winners.join(", ") || "draw") + "</td>", "<td class='r'>" + m.seconds + "s</td>"]);
			    const last = weekly.history.length ? weekly.history[weekly.history.length - 1] : null;
			    document.getElementById("weekly").innerHTML = "Next: " + new Date(weekly.next).toLocaleString()
			      + (weekly.signupsOpen ? " (sign-ups open: " + weekly.signups + ")" : "")
			      + (last ? "<br>Last winner: " + esc(last.winners.join(" & ")) + " (" + last.players + " players)" : "");
			  })
			  .catch(e => { document.getElementById("status").textContent = "Could not load the stats: " + e; });
			</script>
			</body>
			</html>
			""";
}
