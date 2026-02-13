const submitBtn = document.getElementById('submitBtn');
const extractBtn = document.getElementById('extractBtn');
const loadingArea = document.getElementById('loading-area');
const responseArea = document.getElementById('response-area');
const responseContent = document.getElementById('response-content');

submitBtn.addEventListener('click', () => executeRequest('/api/query', true));
extractBtn.addEventListener('click', () => executeRequest('/api/extract', false));

async function executeRequest(endpoint, requireQuery) {
  const query = document.getElementById('query').value.trim();
  const provider = document.getElementById('provider').value;
  const rootPageUrl = document.getElementById('rootPageUrl').value.trim();
  const maxDepth = Number(document.getElementById('maxDepth').value || 5);
  const maxPages = Number(document.getElementById('maxPages').value || 200);

  if (requireQuery && !query) {
    showError('Please enter a query.');
    return;
  }

  setLoading(true);

  try {
    const payload = { rootPageUrl, maxDepth, maxPages };
    if (requireQuery) {
      payload.query = query;
      payload.provider = provider;
    }

    const res = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    const data = await res.json();

    if (data.status === 'error') {
      showError(data.message || 'Unknown error');
    } else if (data.mode === 'extract-only') {
      renderExtraction(data);
    } else {
      renderQueryResponse(data);
    }
  } catch (err) {
    showError('Request failed: ' + err.message);
  } finally {
    setLoading(false);
  }
}

function setLoading(on) {
  submitBtn.disabled = on;
  extractBtn.disabled = on;
  loadingArea.style.display = on ? 'block' : 'none';
  if (on) responseArea.style.display = 'none';
}

function showError(msg) {
  responseArea.style.display = 'block';
  responseContent.innerHTML = `
    <div class="status-bar error"><span class="dot"></span> Error</div>
    <div class="answer-box" style="border-color:#fecaca;background:#fef2f2;color:#991b1b">${esc(msg)}</div>`;
}

function renderQueryResponse(data) {
  responseArea.style.display = 'block';
  const sources = data.sources || [];

  let html = `
    <div class="status-bar success"><span class="dot"></span> Success</div>
    <div class="answer-box">${renderMarkdown(data.answer || '')}</div>
    <div class="meta">
      <span><strong>Provider:</strong>&nbsp;${esc(data.provider)}</span>
      <span><strong>Pages retrieved:</strong>&nbsp;${data.retrievedPageCount}</span>
    </div>`;

  if (sources.length) {
    html += `<div class="sources-header" style="margin-top:20px">Sources (${sources.length})</div>
      <ul class="source-list">${sources.map(s => sourceItem(s, false)).join('')}</ul>`;
  }

  responseContent.innerHTML = html;
}

function renderExtraction(data) {
  responseArea.style.display = 'block';
  const pages = data.pages || [];

  let html = `
    <div class="status-bar success"><span class="dot"></span> Extraction complete</div>
    <div class="meta" style="margin-bottom:16px">
      <span><strong>Mode:</strong>&nbsp;Extract Only</span>
      <span><strong>Pages retrieved:</strong>&nbsp;${data.retrievedPageCount}</span>
    </div>`;

  if (pages.length) {
    html += `<div class="sources-header">Extracted Pages (${pages.length})</div>
      <ul class="source-list">${pages.map(p => sourceItem(p, true)).join('')}</ul>`;
  }

  responseContent.innerHTML = html;
}

function sourceItem(s, showContent) {
  const depthLabel = s.depth != null ? `D${s.depth}` : '';
  let inner = `
    <span class="badge">${esc(depthLabel)}</span>
    <div class="source-info">
      <div class="source-title">${esc(s.title || 'Untitled')}</div>
      ${s.sourceUrl ? `<a href="${esc(s.sourceUrl)}" target="_blank">${esc(s.sourceUrl)}</a>` : ''}`;
  if (showContent && s.content) {
    inner += `<div class="extract-content">${esc(s.content)}</div>`;
  }
  inner += '</div>';
  return `<li class="source-item">${inner}</li>`;
}

function esc(str) {
  if (str == null) return '';
  const d = document.createElement('div');
  d.textContent = String(str);
  return d.innerHTML;
}

function renderMarkdown(text) {
  if (!text) return '';
  return esc(text)
    // ## Headings → styled h3
    .replace(/^## (.+)$/gm, '<h3 class="md-h2">$1</h3>')
    // ### Sub-headings → styled h4
    .replace(/^### (.+)$/gm, '<h4 class="md-h3">$1</h4>')
    // **bold**
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    // *italic*
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    // Bullet lines: - text
    .replace(/^- (.+)$/gm, '<li class="md-li">$1</li>')
    // Wrap consecutive <li> in <ul>
    .replace(/((?:<li class="md-li">.*<\/li>\n?)+)/g, '<ul class="md-ul">$1</ul>')
    // Numbered lines: 1. text
    .replace(/^\d+\.\s+(.+)$/gm, '<li class="md-li">$1</li>')
    // Horizontal rules
    .replace(/^---$/gm, '<hr class="md-hr">')
    // Paragraphs: double newline
    .replace(/\n\n/g, '</p><p class="md-p">')
    // Single newlines inside paragraphs
    .replace(/\n/g, '<br>');
}
