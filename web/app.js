let stocks = [];
let side = 'BUY';
const $ = id => document.getElementById(id);
const money = value => '$' + Number(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const signedMoney = value => (Number(value) >= 0 ? '+' : '') + money(value);

async function api(url, options) {
  const response = await fetch(url, options);
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || 'Something went wrong');
  return data;
}

async function refreshAll() {
  try {
    stocks = await api('/api/market/stocks');
    renderStocks();
    fillSelectors();
    await Promise.all([loadPortfolio(), loadTransactions(), loadQuote()]);
  } catch (error) { console.error(error); }
}

function renderStocks() {
  const filter = $('stock-search').value.toLowerCase();
  $('stock-list').innerHTML = stocks.filter(stock => `${stock.symbol} ${stock.name}`.toLowerCase().includes(filter)).map(stock => `
    <div class="stock-row"><div class="stock-name"><span class="stock-symbol">${stock.symbol.slice(0, 2)}</span><div><strong>${stock.symbol}</strong><small>${stock.name}</small></div></div><span class="price">${money(stock.price)}</span><span class="change ${stock.change >= 0 ? 'positive' : 'negative'}">${stock.change >= 0 ? '+' : ''}${stock.changePct.toFixed(2)}%</span><button class="view-arrow" title="View quote" onclick="selectStock('${stock.symbol}')">›</button></div>`).join('') || '<div class="empty-state">No matching stocks.</div>';
}
function filterStocks() { renderStocks(); }
function fillSelectors() {
  const options = stocks.map(stock => `<option value="${stock.symbol}">${stock.symbol} — ${stock.name}</option>`).join('');
  $('quote-symbol').innerHTML = options; $('trade-symbol').innerHTML = options;
  const last = new Date(); last.setDate(last.getDate() - 1);
  $('quote-date').value = '2026-09-18';
  $('quote-time').innerHTML = Array.from({ length: 14 }, (_, index) => { const minutes = 570 + index * 30; return `<option value="${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}">${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}</option>`; }).join('');
  $('quote-time').value = '16:00'; $('trade-symbol').onchange = updateOrderTotal; $('trade-quantity').oninput = updateOrderTotal;
}
function selectStock(symbol) { $('quote-symbol').value = symbol; $('trade-symbol').value = symbol; loadQuote(); updateOrderTotal(); document.querySelector('.quote-panel').scrollIntoView({ behavior: 'smooth', block: 'center' }); }
async function loadQuote() {
  if (!$('quote-symbol').value) return;
  const quote = await api(`/api/market/quote?symbol=${$('quote-symbol').value}&date=${$('quote-date').value}&time=${$('quote-time').value}`);
  $('quote-name').textContent = `${quote.symbol} · ${quote.name}`; $('quote-price').textContent = money(quote.price); $('quote-change').textContent = `${quote.change >= 0 ? '+' : ''}${quote.change.toFixed(2)} (${quote.changePct.toFixed(2)}%) · ${quote.date} ${quote.time}`; $('quote-change').className = `quote-change ${quote.change >= 0 ? 'positive' : 'negative'}`; $('snapshot-time').textContent = `${quote.date} / ${quote.time}`; updateOrderTotal();
}
async function loadPortfolio() {
  const portfolio = await api('/api/portfolio');
  $('portfolio-value').textContent = money(portfolio.cash + portfolio.value); $('cash-value').textContent = money(portfolio.cash); $('invested-value').textContent = money(portfolio.invested); $('portfolio-pnl').textContent = `${signedMoney(portfolio.pnl)} total return`; $('portfolio-pnl').className = portfolio.pnl >= 0 ? 'positive' : 'negative'; $('holding-count').textContent = `${portfolio.holdings.length} position${portfolio.holdings.length === 1 ? '' : 's'}`; $('position-count').textContent = portfolio.holdings.length;
  $('holdings-list').innerHTML = portfolio.holdings.length ? portfolio.holdings.map(item => `<div class="holding-row"><div><strong>${item.symbol}</strong><small>${item.name}</small></div><span class="number">${item.quantity} sh</span><span class="number">${money(item.value)}</span><span class="number ${item.pnl >= 0 ? 'positive' : 'negative'}">${signedMoney(item.pnl)}</span></div>`).join('') : '<div class="empty-state">Your portfolio is waiting for its first position.</div>';
}
async function loadTransactions() {
  const transactions = await api('/api/transactions');
  $('transaction-list').innerHTML = transactions.length ? transactions.map(item => `<div class="transaction-row"><span><b class="${item.side === 'BUY' ? 'side-buy' : 'side-sell'}">${item.side}</b> ${item.symbol}</span><span>${item.quantity}</span><span>${money(item.price)}</span><span>${money(item.total)}</span><span>${item.timestamp}</span></div>`).join('') : '<div class="empty-state">Executed orders will appear here.</div>';
}
function setSide(nextSide) { side = nextSide; $('buy-tab').classList.toggle('active', side === 'BUY'); $('sell-tab').classList.toggle('active', side === 'SELL'); $('order-action').textContent = side === 'BUY' ? 'Buy' : 'Sell'; }
async function updateOrderTotal() { if (!$('trade-symbol').value) return; try { const quote = await api(`/api/market/quote?symbol=${$('trade-symbol').value}&date=2026-09-18&time=16:00`); $('order-total').textContent = money(quote.price * Number($('trade-quantity').value || 0)); } catch (_) {} }
async function submitTrade(event) { event.preventDefault(); const message = $('trade-message'); message.textContent = ''; try { await api('/api/trade', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ symbol: $('trade-symbol').value, side, quantity: $('trade-quantity').value }) }); message.textContent = `${side === 'BUY' ? 'Buy' : 'Sell'} order executed.`; await Promise.all([loadPortfolio(), loadTransactions()]); } catch (error) { message.textContent = error.message; message.className = 'form-message negative'; } }
refreshAll();
