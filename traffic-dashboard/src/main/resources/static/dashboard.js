const status = document.querySelector('#status');
const table = document.querySelector('#zones');
const filter = document.querySelector('#filter');
const empty = document.querySelector('#empty');
const zones = new Map();
const states = ['DESPEJADA', 'MODERADA', 'CONGESTIONADA'];
const number = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 6 });
const date = new Intl.DateTimeFormat('es-CO', { dateStyle: 'short', timeStyle: 'medium' });

function render() {
  const search = filter.value.trim().toLocaleLowerCase('es-CO');
  const counts = Object.fromEntries(states.map(state => [state, 0]));
  const fragment = document.createDocumentFragment();

  for (const event of zones.values()) {
    counts[event.congestionState]++;
    if (!event.zoneId.toLocaleLowerCase('es-CO').includes(search)) continue;

    const row = document.createElement('tr');
    row.insertCell().textContent = event.zoneId;
    row.insertCell().textContent = number.format(event.density);
    const badge = document.createElement('span');
    badge.className = 'badge';
    badge.dataset.state = event.congestionState;
    badge.textContent = event.congestionState;
    row.insertCell().append(badge);
    row.insertCell().textContent = date.format(event.timestamp);
    fragment.append(row);
  }

  empty.hidden = fragment.childElementCount > 0;
  empty.textContent = zones.size ? 'No hay zonas que coincidan.' : 'Esperando estados de tráfico.';
  table.replaceChildren(fragment);
  document.querySelector('#total').textContent = zones.size;
  for (const state of states) document.getElementById(state).textContent = counts[state];
}

function connect() {
  const endpoint = new URL('/ws/zones', location.href);
  endpoint.protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const socket = new WebSocket(endpoint);

  socket.onopen = () => {
    zones.clear();
    render();
    status.dataset.connected = 'true';
    status.textContent = 'Conectado';
  };

  socket.onmessage = ({ data }) => {
    let event;
    try {
      event = JSON.parse(data);
    } catch {
      return;
    }

    if (typeof event?.zoneId !== 'string' || !Number.isFinite(event.density)
        || !states.includes(event.congestionState)) return;
    const timestamp = Date.parse(event.measuredAt);
    if (!Number.isFinite(timestamp) || timestamp < (zones.get(event.zoneId)?.timestamp ?? -Infinity)) return;
    zones.set(event.zoneId, { ...event, timestamp });
    render();
  };

  socket.onclose = () => {
    status.dataset.connected = 'false';
    status.textContent = 'Reconectando…';
    setTimeout(connect, 2000);
  };
}

filter.addEventListener('input', render);
connect();
