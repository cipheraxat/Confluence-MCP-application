const submitBtn = document.getElementById('submitBtn');
const extractBtn = document.getElementById('extractBtn');
const addUrlBtn = document.getElementById('addUrlBtn');
const urlContainer = document.getElementById('urlContainer');
const loadingArea = document.getElementById('loading-area');
const responseArea = document.getElementById('response-area');
const responseContent = document.getElementById('response-content');

// URL management
addUrlBtn.addEventListener('click', addUrlInput);
urlContainer.addEventListener('click', handleUrlRemove);

function addUrlInput() {
  const urlGroups = urlContainer.querySelectorAll('.url-input-group');
  if (urlGroups.length >= 5) return; // Limit to 5 URLs
  
  const newGroup = document.createElement('div');
  newGroup.className = 'url-input-group';
  newGroup.innerHTML = `
    <input type="text" class="url-input" placeholder="https://your-confluence-url.com/pages/12345/Page+Title" />
    <button type="button" class="remove-url-btn">&times;</button>
  `;
  urlContainer.appendChild(newGroup);
  updateRemoveButtons();
}

function handleUrlRemove(e) {
  if (e.target.classList.contains('remove-url-btn')) {
    e.target.closest('.url-input-group').remove();
    updateRemoveButtons();
  }
}

function updateRemoveButtons() {
  const urlGroups = urlContainer.querySelectorAll('.url-input-group');
  urlGroups.forEach((group, index) => {
    const removeBtn = group.querySelector('.remove-url-btn');
    removeBtn.style.display = urlGroups.length > 1 ? 'flex' : 'none';
  });
}

// Initialize remove buttons
updateRemoveButtons();

submitBtn.addEventListener('click', () => executeRequest('/api/query', true));
extractBtn.addEventListener('click', () => executeRequest('/api/extract', false));

async function executeRequest(endpoint, requireQuery) {
  const query = document.getElementById('query').value.trim();
  const provider = document.getElementById('provider').value;
  
  // Collect all URLs
  const urlInputs = urlContainer.querySelectorAll('.url-input');
  const rootPageUrls = Array.from(urlInputs)
    .map(input => input.value.trim())
    .filter(url => url.length > 0);
  
  const maxDepth = Number(document.getElementById('maxDepth').value || 5);
  const maxPages = Number(document.getElementById('maxPages').value || 200);

  if (requireQuery && !query) {
    showError('Please enter a query.');
    return;
  }
  
  if (rootPageUrls.length === 0) {
    showError('Please enter at least one Confluence URL.');
    return;
  }

  setLoading(true);

  try {
    const payload = { 
      rootPageUrls, 
      maxDepth, 
      maxPages 
    };
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
  const rootUrls = data.rootPageUrls || (data.rootPageUrl ? [data.rootPageUrl] : []);
  const sources = data.sources || [];

  let html = `
    <div class="status-bar success"><span class="dot"></span> Success</div>
    <div class="answer-box">${renderMarkdown(data.answer || '', sources)}</div>
    <div class="meta">
      <span><strong>Provider:</strong>&nbsp;${esc(data.provider)}</span>
      <span><strong>Pages retrieved:</strong>&nbsp;${data.retrievedPageCount}</span>
      <span><strong>Sources:</strong>&nbsp;${rootUrls.length} URL${rootUrls.length !== 1 ? 's' : ''}</span>
    </div>`;

  if (rootUrls.length) {
    html += `<div class="sources-header" style="margin-top:20px">Sources</div>
      <div class="source-links">${rootUrls.map(url => `<a href="${esc(url)}" target="_blank" class="source-link">${esc(url)}</a>`).join('')}</div>`;
  }

  responseContent.innerHTML = html;
}

function renderExtraction(data) {
  responseArea.style.display = 'block';
  const rootUrls = data.rootPageUrls || (data.rootPageUrl ? [data.rootPageUrl] : []);

  let html = `
    <div class="status-bar success"><span class="dot"></span> Extraction complete</div>
    <div class="meta" style="margin-bottom:16px">
      <span><strong>Mode:</strong>&nbsp;Extract Only</span>
      <span><strong>Pages retrieved:</strong>&nbsp;${data.retrievedPageCount}</span>
      <span><strong>Sources:</strong>&nbsp;${rootUrls.length} URL${rootUrls.length !== 1 ? 's' : ''}</span>
    </div>`;

  if (rootUrls.length) {
    html += `<div class="sources-header">Sources</div>
      <div class="source-links">${rootUrls.map(url => `<a href="${esc(url)}" target="_blank" class="source-link">${esc(url)}</a>`).join('')}</div>`;
  }

  responseContent.innerHTML = html;
}

function sourceLink(s) {
  if (!s.sourceUrl) return '';
  return `<a href="${esc(s.sourceUrl)}" target="_blank" class="source-link">${esc(s.title || 'Untitled')}</a>`;
}

function esc(str) {
  if (str == null) return '';
  const d = document.createElement('div');
  d.textContent = String(str);
  return d.innerHTML;
}

function renderMarkdown(text, sources) {
  if (!text) return '';
  let rendered = esc(text)
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

  // Inject links into Sources Referenced section
  if (sources && sources.length) {
    for (const s of sources) {
      if (s.title && s.sourceUrl) {
        const escapedTitle = esc(s.title);
        const link = `<a href="${esc(s.sourceUrl)}" target="_blank" class="source-link" style="display:inline;padding:2px 6px;font-size:inherit;">${escapedTitle}</a>`;
        // Replace occurrences of the title text within list items (Sources Referenced section)
        rendered = rendered.split(escapedTitle).join(link);
      }
    }
  }

  return rendered;
}
