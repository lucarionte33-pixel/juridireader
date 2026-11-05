import re
import os
import sys
import sqlite3
import unicodedata
import requests
from bs4 import BeautifulSoup
from datetime import datetime

LAWS_CONFIG = [
    {
        "code": "CF88",
        "title": "Constituição Federal de 1988",
        "category": "Constitucional",
        "url": "https://www.planalto.gov.br/ccivil_03/constituicao/constituicao.htm"
    },
    {
        "code": "CC",
        "title": "Código Civil (Lei nº 10.406/2002)",
        "category": "Civil",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/2002/l10406compilada.htm"
    },
    {
        "code": "CP",
        "title": "Código Penal (Decreto-Lei nº 2.848/1940)",
        "category": "Penal",
        "url": "https://www.planalto.gov.br/ccivil_03/decreto-lei/del2848compilado.htm"
    },
    {
        "code": "CPC",
        "title": "Código de Processo Civil (Lei nº 13.105/2015)",
        "category": "Processual Civil",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2015/lei/l13105.htm"
    },
    {
        "code": "CPP",
        "title": "Código de Processo Penal (Decreto-Lei nº 3.689/1941)",
        "category": "Processual Penal",
        "url": "https://www.planalto.gov.br/ccivil_03/decreto-lei/del3689compilado.htm"
    },
    {
        "code": "CLT",
        "title": "Consolidação das Leis do Trabalho (Decreto-Lei nº 5.452/1943)",
        "category": "Trabalhista",
        "url": "https://www.planalto.gov.br/ccivil_03/decreto-lei/del5452compilado.htm"
    },
    {
        "code": "CDC",
        "title": "Código de Defesa do Consumidor (Lei nº 8.078/1990)",
        "category": "Consumidor",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l8078compilado.htm"
    }
]

SESSION_HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'pt-BR,pt;q=0.9,en-US;q=0.8',
    'Connection': 'keep-alive'
}

def remove_accents(input_str: str) -> str:
    nfkd_form = unicodedata.normalize('NFKD', input_str)
    return "".join([c for c in nfkd_form if not unicodedata.combining(c)])

def normalize_article_num(raw_num: str) -> str:
    clean = raw_num.strip().rstrip('.-').replace('º', '').replace('o', '').replace('ª', '').replace('a', '')
    clean = re.sub(r'[^0-9A-Za-z]', '_', clean)
    clean = re.sub(r'_+', '_', clean).strip('_')
    return clean

def extract_law(session, config):
    code = config["code"]
    title = config["title"]
    category = config["category"]
    url = config["url"]
    
    print(f"[{code}] Baixando de {url}...")
    try:
        resp = session.get(url, headers=SESSION_HEADERS, timeout=30)
        resp.raise_for_status()
    except Exception as e:
        print(f"[{code}] Erro no download: {e}")
        return []

    # Converter encoding para UTF-8
    # Se detectado ISO-8859-1 ou Windows-1252
    raw_content = resp.content
    try:
        html_text = raw_content.decode('cp1252')
    except Exception:
        html_text = raw_content.decode('latin-1', errors='replace')
        
    soup = BeautifulSoup(html_text, 'html.parser')
    
    # Remover tags revogadas
    for tag in soup.find_all(['strike', 's', 'del']):
        tag.decompose()
        
    # Extrair parágrafos
    # Planalto usa <p>, <div>, <font> e <blockquote> para texto de leis
    elements = soup.find_all(['p', 'div', 'font'])
    
    # Regex para identificar início de artigo
    # Ex: Art. 1º, Art. 1o, Art. 1º-A, Art. 10., Artigo 5º, Art. 1.234
    art_regex = re.compile(r'^\s*Art(?:\.|\b|igo)\s*([0-9]+(?:[\.\-][0-9A-Za-z]+|[ºoªa\.\-A-Za-z0-9])*)', re.IGNORECASE)
    
    seen_texts = set()
    paragraphs = []
    for el in elements:
        text = el.get_text(' ', strip=True)
        if text and text not in seen_texts:
            # Não adicionar duplicados se o pai já contiver o texto exato
            seen_texts.add(text)
            paragraphs.append(text)
            
    articles = []
    current_art = None
    order_idx = 0
    preamble_lines = []
    
    for p in paragraphs:
        # Pular cabeçalhos genéricos
        if len(p) < 3:
            continue
            
        m = art_regex.match(p)
        if m:
            if current_art:
                articles.append(current_art)
                
            raw_art_num = m.group(1).rstrip('.:- ')
            semantic_num = normalize_article_num(raw_art_num)
            art_id = f"{code}_ART_{semantic_num}"
            
            # Garantir ID único caso ocorra repetição
            dup_count = 1
            original_id = art_id
            existing_ids = {a["id"] for a in articles}
            while art_id in existing_ids:
                dup_count += 1
                art_id = f"{original_id}_{dup_count}"
                
            display_num = f"Art. {raw_art_num}"
            if not display_num.endswith(('.', 'º', 'o', 'ª')):
                if raw_art_num.isdigit() and int(raw_art_num) <= 9:
                    display_num += "º"
                    
            order_idx += 1
            current_art = {
                "id": art_id,
                "law_code": code,
                "law_title": title,
                "category": category,
                "article_number": display_num,
                "content": p,
                "order_index": order_idx
            }
        else:
            if current_art:
                current_art["content"] += "\n\n" + p
            else:
                if len(preamble_lines) < 15:
                    preamble_lines.append(p)
                    
    if current_art:
        articles.append(current_art)
        
    # Se houver preâmbulo, adicionar como item de abertura da lei
    if preamble_lines:
        order_idx += 1
        preamble_art = {
            "id": f"{code}_PREAMBULO",
            "law_code": code,
            "law_title": title,
            "category": category,
            "article_number": "Preâmbulo",
            "content": "\n\n".join(preamble_lines),
            "order_index": 0
        }
        articles.insert(0, preamble_art)
        
    print(f"[{code}] {len(articles)} artigos extraídos com sucesso!")
    return articles

def generate_database(output_path: str):
    session = requests.Session()
    all_articles = []
    law_summaries = []
    
    for config in LAWS_CONFIG:
        arts = extract_law(session, config)
        if arts:
            all_articles.extend(arts)
            law_summaries.append((
                config["code"],
                config["title"],
                config["category"],
                len(arts),
                datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            ))
            
    print(f"\nTotal de artigos coletados: {len(all_articles)}")
    
    if os.path.exists(output_path):
        os.remove(output_path)
        
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    conn = sqlite3.connect(output_path)
    cur = conn.cursor()
    
    # 1. Tabela de Leis (atualizável sem tocar dados do usuário)
    cur.execute("""
    CREATE TABLE articles (
        id TEXT PRIMARY KEY NOT NULL,
        law_code TEXT NOT NULL,
        law_title TEXT NOT NULL,
        category TEXT NOT NULL,
        article_number TEXT NOT NULL,
        content TEXT NOT NULL,
        order_index INTEGER NOT NULL
    )
    """)
    cur.execute("CREATE INDEX idx_articles_law_code ON articles(law_code);")
    cur.execute("CREATE INDEX idx_articles_category ON articles(category);")
    
    # 2. Metadados das Leis
    cur.execute("""
    CREATE TABLE laws_metadata (
        code TEXT PRIMARY KEY NOT NULL,
        title TEXT NOT NULL,
        category TEXT NOT NULL,
        article_count INTEGER NOT NULL,
        updated_at TEXT NOT NULL
    )
    """)
    cur.executemany("""
    INSERT INTO laws_metadata (code, title, category, article_count, updated_at)
    VALUES (?, ?, ?, ?, ?)
    """, law_summaries)
    
    # 3. Inserção dos Artigos
    cur.executemany("""
    INSERT INTO articles (id, law_code, law_title, category, article_number, content, order_index)
    VALUES (:id, :law_code, :law_title, :category, :article_number, :content, :order_index)
    """, all_articles)
    
    # 4. FTS5 Virtual Table (sem acentos, case-insensitive)
    cur.execute("""
    CREATE VIRTUAL TABLE articles_fts USING fts5(
        id UNINDEXED,
        law_code,
        article_number,
        content,
        tokenize = 'unicode61 remove_diacritics 2'
    )
    """)
    
    cur.execute("""
    INSERT INTO articles_fts (id, law_code, article_number, content)
    SELECT id, law_code, article_number, content FROM articles
    """)
    
    # 5. Tabelas desacopladas de personalização do usuário (Room)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS user_favorites (
        article_id TEXT PRIMARY KEY NOT NULL,
        created_at INTEGER NOT NULL
    )
    """)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS user_notes (
        article_id TEXT PRIMARY KEY NOT NULL,
        note_text TEXT NOT NULL,
        updated_at INTEGER NOT NULL
    )
    """)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS user_highlights (
        article_id TEXT PRIMARY KEY NOT NULL,
        color TEXT NOT NULL,
        updated_at INTEGER NOT NULL
    )
    """)
    
    # 6. Room Master Table (garante compatibilidade e validação perfeita no Room)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS room_master_table (
        id INTEGER PRIMARY KEY,
        identity_hash TEXT
    )
    """)
    cur.execute("""
    INSERT OR REPLACE INTO room_master_table (id, identity_hash)
    VALUES (42, '0e20eb9cc935462f4786918d588f0228')
    """)
    cur.execute("PRAGMA user_version = 1;")
    
    conn.commit()
    conn.close()
    
    db_size = os.path.getsize(output_path)
    print(f"\nBanco de dados SQLite gerado com sucesso!")
    print(f"Caminho: {output_path}")
    print(f"Tamanho: {db_size / (1024*1024):.2f} MB")

if __name__ == "__main__":
    out_db = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "juridireader.db")
    if len(sys.argv) > 1:
        out_db = sys.argv[1]
    generate_database(out_db)
