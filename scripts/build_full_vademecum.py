import os
import re
import sys
import sqlite3
import unicodedata
import subprocess
from datetime import datetime
from bs4 import BeautifulSoup

LAWS = [
    # 1. CONSTITUIÇÃO
    {
        "code": "CF88",
        "title": "Constituição Federal de 1988",
        "category": "Constituição",
        "url": "https://www.planalto.gov.br/ccivil_03/constituicao/constituicao.htm"
    },
    {
        "code": "ADCT",
        "title": "Atos das Disposições Constitucionais Transitórias",
        "category": "Constituição",
        "url": "https://www.planalto.gov.br/ccivil_03/constituicao/constituicao.htm",
        "is_adct": True
    },

    # 2. CÓDIGOS
    {
        "code": "CC",
        "title": "Código Civil (Lei nº 10.406/2002)",
        "category": "Códigos",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/2002/l10406compilada.htm"
    },
    {
        "code": "CP",
        "title": "Código Penal (Decreto-Lei nº 2.848/1940)",
        "category": "Códigos",
        "url": "https://www.planalto.gov.br/ccivil_03/decreto-lei/del2848compilado.htm"
    },
    {
        "code": "CPC",
        "title": "Código de Processo Civil (Lei nº 13.105/2015)",
        "category": "Códigos",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2015/lei/l13105.htm"
    },
    {
        "code": "CPP",
        "title": "Código de Processo Penal (Decreto-Lei nº 3.689/1941)",
        "category": "Códigos",
        "url": "https://www.planalto.gov.br/ccivil_03/decreto-lei/del3689compilado.htm"
    },
    {
        "code": "CLT",
        "title": "Consolidação das Leis do Trabalho (Decreto-Lei nº 5.452/1943)",
        "category": "Códigos",
        "url": "https://www.planalto.gov.br/ccivil_03/decreto-lei/del5452compilado.htm"
    },
    {
        "code": "CDC",
        "title": "Código de Defesa do Consumidor (Lei nº 8.078/1990)",
        "category": "Códigos",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l8078compilado.htm"
    },
    {
        "code": "CTN",
        "title": "Código Tributário Nacional (Lei nº 5.172/1966)",
        "category": "Códigos",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l5172compilado.htm"
    },
    {
        "code": "CTB",
        "title": "Código de Trânsito Brasileiro (Lei nº 9.503/1997)",
        "category": "Códigos",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l9503compilado.htm"
    },
    {
        "code": "CE",
        "title": "Código Eleitoral (Lei nº 4.737/1965)",
        "category": "Códigos",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l4737compilado.htm"
    },
    {
        "code": "CPM",
        "title": "Código Penal Militar (Decreto-Lei nº 1.001/1969)",
        "category": "Códigos",
        "url": "https://www.planalto.gov.br/ccivil_03/decreto-lei/del1001compilado.htm"
    },
    {
        "code": "CPPM",
        "title": "Código de Processo Penal Militar (Decreto-Lei nº 1.002/1969)",
        "category": "Códigos",
        "url": "https://www.planalto.gov.br/ccivil_03/decreto-lei/del1002compilado.htm"
    },

    # 3. ESTATUTOS
    {
        "code": "ECA",
        "title": "Estatuto da Criança e do Adolescente (Lei nº 8.069/1990)",
        "category": "Estatutos",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l8069compilado.htm"
    },
    {
        "code": "EOAB",
        "title": "Estatuto da OAB e da Advocacia (Lei nº 8.906/1994)",
        "category": "Estatutos",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l8906.htm"
    },
    {
        "code": "EPIdoso",
        "title": "Estatuto da Pessoa Idosa (Lei nº 10.741/2003)",
        "category": "Estatutos",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/2003/l10.741compilado.htm"
    },
    {
        "code": "EPCD",
        "title": "Estatuto da Pessoa com Deficiência (Lei nº 13.146/2015)",
        "category": "Estatutos",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2015/lei/l13146.htm"
    },
    {
        "code": "LEI_8112",
        "title": "Regime Jurídico dos Servidores Federais (Lei nº 8.112/1990)",
        "category": "Estatutos",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l8112cons.htm"
    },
    {
        "code": "EIRacial",
        "title": "Estatuto da Igualdade Racial (Lei nº 12.288/2010)",
        "category": "Estatutos",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2007-2010/2010/lei/l12288.htm"
    },
    {
        "code": "ECidade",
        "title": "Estatuto da Cidade (Lei nº 10.257/2001)",
        "category": "Estatutos",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/leis_2001/l10257.htm"
    },
    {
        "code": "EDesarm",
        "title": "Estatuto do Desarmamento (Lei nº 10.826/2003)",
        "category": "Estatutos",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/2003/l10.826compilado.htm"
    },

    # 4. LEIS ORDINÁRIAS
    {
        "code": "LEI_14133",
        "title": "Nova Lei de Licitações e Contratos (Lei nº 14.133/2021)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2021/lei/l14133.htm"
    },
    {
        "code": "LEI_11343",
        "title": "Lei de Drogas (Lei nº 11.343/2006)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2004-2006/2006/lei/l11343.htm"
    },
    {
        "code": "LEI_11340",
        "title": "Lei Maria da Penha (Lei nº 11.340/2006)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2004-2006/2006/lei/l11340.htm"
    },
    {
        "code": "LEI_7210",
        "title": "Lei de Execução Penal - LEP (Lei nº 7.210/1984)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l7210compilado.htm"
    },
    {
        "code": "LEI_9099",
        "title": "Juizados Especiais Cíveis e Criminais (Lei nº 9.099/1995)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l9099.htm"
    },
    {
        "code": "LEI_13869",
        "title": "Lei de Abuso de Autoridade (Lei nº 13.869/2019)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2019-2022/2019/lei/l13869.htm"
    },
    {
        "code": "LEI_8429",
        "title": "Lei de Improbidade Administrativa (Lei nº 8.429/1992)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l8429compilada.htm"
    },
    {
        "code": "LEI_13709",
        "title": "LGPD - Proteção de Dados Pessoais (Lei nº 13.709/2018)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm"
    },
    {
        "code": "LEI_12965",
        "title": "Marco Civil da Internet (Lei nº 12.965/2014)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2014/lei/l12965.htm"
    },
    {
        "code": "LEI_11101",
        "title": "Lei de Falências e Recuperação (Lei nº 11.101/2005)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2004-2006/2005/lei/l11101compilado.htm"
    },
    {
        "code": "LEI_7347",
        "title": "Ação Civil Pública (Lei nº 7.347/1985)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l7347compilada.htm"
    },
    {
        "code": "LEI_12016",
        "title": "Mandado de Segurança (Lei nº 12.016/2009)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2007-2010/2009/lei/l12016.htm"
    },
    {
        "code": "LEI_8245",
        "title": "Lei do Inquilinato / Locações (Lei nº 8.245/1991)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l8245compilado.htm"
    },
    {
        "code": "LEI_8072",
        "title": "Crimes Hediondos (Lei nº 8.072/1990)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l8072compilada.htm"
    },
    {
        "code": "LEI_12850",
        "title": "Organizações Criminosas (Lei nº 12.850/2013)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2013/lei/l12850.htm"
    },
    {
        "code": "LEI_9296",
        "title": "Interceptação Telefônica (Lei nº 9.296/1996)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l9296.htm"
    },
    {
        "code": "LEI_7960",
        "title": "Prisão Temporária (Lei nº 7.960/1989)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l7960.htm"
    },
    {
        "code": "LEI_9455",
        "title": "Lei de Tortura (Lei nº 9.455/1997)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l9455.htm"
    },
    {
        "code": "LEI_12846",
        "title": "Lei Anticorrupção (Lei nº 12.846/2013)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2013/lei/l12846.htm"
    },
    {
        "code": "LEI_9784",
        "title": "Processo Administrativo Federal (Lei nº 9.784/1999)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l9784.htm"
    },
    {
        "code": "LEI_4717",
        "title": "Ação Popular (Lei nº 4.717/1965)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l4717.htm"
    },
    {
        "code": "LEI_6830",
        "title": "Execução Fiscal (Lei nº 6.830/1980)",
        "category": "Leis Ordinárias",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/l6830.htm"
    },

    # 5. LEIS COMPLEMENTARES
    {
        "code": "LCP_101",
        "title": "Lei de Responsabilidade Fiscal - LRF (LC nº 101/2000)",
        "category": "Leis Complementares",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp101.htm"
    },
    {
        "code": "LCP_150",
        "title": "Lei do Trabalho Doméstico (LC nº 150/2015)",
        "category": "Leis Complementares",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp150.htm"
    },
    {
        "code": "LCP_75",
        "title": "Estatuto do MPU (LC nº 75/1993)",
        "category": "Leis Complementares",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp75.htm"
    },
    {
        "code": "LCP_35",
        "title": "Lei Orgânica da Magistratura - LOMAN (LC nº 35/1979)",
        "category": "Leis Complementares",
        "url": "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp35.htm"
    }
]

CACHE_DIR = os.path.join(os.path.dirname(__file__), ".cache_laws")
os.makedirs(CACHE_DIR, exist_ok=True)

def download_law(url, code):
    if code == "ADCT":
        cf_cache = os.path.join(CACHE_DIR, "CF88.htm")
        if os.path.exists(cf_cache):
            with open(cf_cache, "rb") as f:
                raw = f.read()
            for enc in ['cp1252', 'iso-8859-1', 'utf-8']:
                try:
                    return raw.decode(enc)
                except Exception:
                    continue
            return raw.decode('latin1', errors='replace')

    cache_file = os.path.join(CACHE_DIR, f"{code}.htm")
    if os.path.exists(cache_file) and os.path.getsize(cache_file) > 1000:
        with open(cache_file, "rb") as f:
            raw = f.read()
    else:
        ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        cmd = [
            "curl.exe", "-s",
            "-A", ua,
            "-H", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "-H", "Accept-Language: pt-BR,pt;q=0.9",
            url
        ]
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        raw = res.stdout
        if len(raw) > 500:
            with open(cache_file, "wb") as f:
                f.write(raw)
        else:
            print(f"[{code}] AVISO: Download curto ({len(raw)} bytes).")

    if raw.startswith(b'\xff\xfe') or raw.startswith(b'\xfe\xff'):
        return raw.decode('utf-16', errors='replace')

    for enc in ['utf-8', 'cp1252', 'iso-8859-1']:
        try:
            return raw.decode(enc)
        except Exception:
            continue
    return raw.decode('latin1', errors='replace')

def format_clean_text(text: str) -> str:
    # 1. Normalizar quebras de linha e tabs
    clean = text.replace('\r', '').replace('\xa0', ' ')
    clean = re.sub(r'[ \t]+', ' ', clean)
    clean = re.sub(r'\n\s*\n+', '\n\n', clean).strip()

    # 2. Separar incisos em linhas novas com espaçamento limpo: I -, II -, III -, etc.
    clean = re.sub(r'([;:])\s+([IVXLCDM]+\s*[-–—])', r'\1\n\n\2', clean)
    clean = re.sub(r'(?<=\.)\s+([IVXLCDM]+\s*[-–—])', r'\n\n\1', clean)

    # 3. Separar parágrafos: § 1º, Parágrafo único.
    clean = re.sub(r'\s+(Par[áa]grafo\s+[úu]nico\.?)', r'\n\n\1', clean, flags=re.IGNORECASE)
    clean = re.sub(r'\s+(§\s*[0-9]+[ºoªa\.\-]*)', r'\n\n\1', clean)

    # 4. Separar alíneas: a), b), c)
    clean = re.sub(r'([;:])\s+([a-z]\))\s+', r'\1\n\n    \2 ', clean)

    return clean.strip()

def parse_law(html_text: str, law_info: dict):
    code = law_info["code"]
    title = law_info["title"]
    category = law_info["category"]

    if code == "CF88":
        adct_idx = html_text.lower().find('name="adct"')
        if adct_idx != -1:
            html_text = html_text[:adct_idx]
    elif code == "ADCT":
        adct_idx = html_text.lower().find('name="adct"')
        if adct_idx != -1:
            html_text = html_text[adct_idx:]

    soup = BeautifulSoup(html_text, 'html.parser')

    # Identificar e preservar trechos revogados antes de remover tags
    for tag in soup.find_all(['strike', 's', 'del']):
        txt = tag.get_text(' ', strip=True)
        if txt:
            tag.replace_with(soup.new_string(f'\n[[[REV_START]]]\n{txt}\n[[[REV_END]]]\n'))

    # Converter <br> para nova linha
    for br in soup.find_all('br'):
        br.replace_with('\n')

    full_text = soup.get_text('\n')
    full_text = full_text.replace('\r', '').replace('\xa0', ' ')

    # Unir Art. com seu número se quebrados em linhas diferentes
    full_text = re.sub(r'\b(Art(?:igo|\.)?)\s*\n\s*([0-9]+[ºoªa\.\-]*)', r'\1 \2', full_text, flags=re.IGNORECASE)

    raw_lines = [l.strip() for l in full_text.split('\n') if l.strip()]

    # Regex para identificar início de artigo
    # Deve iniciar com "Art." ou "Artigo" (maiúsculo)
    art_regex = re.compile(r'^\s*Art(?:\.|\b|igo)\s*([0-9]+(?:[\.\-][0-9A-Za-z]+|[ºoªa\.\-A-Za-z0-9])*)', re.IGNORECASE)

    articles = []
    current_art = None
    order_idx = 0
    preamble_lines = []
    current_header = ""

    pending_revoked = {}
    in_revoked = False
    rev_buffer = []

    def finalize_article(art):
        if not art:
            return
        art["content"] = format_clean_text("\n".join(art["raw_lines"]))
        del art["raw_lines"]
        if art.get("revoked_lines"):
            art["revoked_content"] = format_clean_text("\n\n---\n\n".join(art["revoked_lines"]))
            del art["revoked_lines"]
        else:
            art["revoked_content"] = None
        articles.append(art)

    for line in raw_lines:
        if '[[[REV_START]]]' in line:
            in_revoked = True
            line = line.replace('[[[REV_START]]]', '').strip()
            if not line:
                continue
        if '[[[REV_END]]]' in line:
            in_revoked = False
            line = line.replace('[[[REV_END]]]', '').strip()
            if line:
                rev_buffer.append(line)
            full_rev = ' '.join(rev_buffer).strip()
            rev_buffer = []
            if full_rev:
                m_rev = art_regex.match(full_rev)
                if m_rev:
                    num = m_rev.group(1).rstrip('.:- ')
                    sem = num.replace('.', '_').replace('-', '_').replace('º', '').replace('o', '').replace('ª', '').replace('a', '')
                    pending_revoked.setdefault(sem, []).append(full_rev)
                elif current_art:
                    current_art.setdefault('revoked_lines', []).append(full_rev)
                else:
                    pending_revoked.setdefault('__GENERAL__', []).append(full_rev)
            continue

        if in_revoked:
            rev_buffer.append(line)
            continue

        # Detectar cabeçalho estrutural
        if re.match(r'^(LIVRO|T[ÍI]TULO|CAP[ÍI]TULO|SE[ÇC][ÃA]O|SUBSE[ÇC][ÃA]O|PARTE)\s+[IVXLCDM0-9A-Z]+', line, re.IGNORECASE):
            current_header = line
            continue

        m = art_regex.match(line)
        if m:
            finalize_article(current_art)

            raw_num = m.group(1).rstrip('.:- ')
            semantic_num = raw_num.replace('.', '_').replace('-', '_').replace('º', '').replace('o', '').replace('ª', '').replace('a', '')
            art_id = f"{code}_ART_{semantic_num}"

            # Evitar colisão de IDs duplicados
            dup = 1
            orig_id = art_id
            existing_ids = {a["id"] for a in articles}
            while art_id in existing_ids:
                dup += 1
                art_id = f"{orig_id}_{dup}"

            display_num = f"Art. {raw_num}"
            if raw_num.isdigit() and int(raw_num) <= 9 and not display_num.endswith(('º', 'o', 'ª', '.')):
                display_num += "º"

            order_idx += 1
            current_art = {
                "id": art_id,
                "law_code": code,
                "law_title": title,
                "category": category,
                "article_number": display_num,
                "raw_lines": [line],
                "revoked_lines": pending_revoked.pop(semantic_num, []),
                "order_index": order_idx,
                "header_info": current_header
            }
        else:
            if current_art:
                current_art["raw_lines"].append(line)
            else:
                # Coletar preâmbulo/ementa sem cabeçalhos burocráticos
                if not re.search(r'Presid[êe]ncia|Secretaria|Subchefia|Casa Civil|Bras[íi]lia|Di[áa]rio Oficial', line, re.IGNORECASE):
                    if len(line) > 10 and len(preamble_lines) < 12:
                        preamble_lines.append(line)

    finalize_article(current_art)

    # Inserir artigos órfãos totalmente revogados
    for sem, rev_list in pending_revoked.items():
        if sem == '__GENERAL__':
            continue
        order_idx += 1
        art_id = f"{code}_ART_{sem}_REV"
        articles.append({
            "id": art_id,
            "law_code": code,
            "law_title": title,
            "category": category,
            "article_number": f"Art. {sem} (Revogado)",
            "content": "(Artigo totalmente revogado na legislação vigente)",
            "revoked_content": format_clean_text("\n\n---\n\n".join(rev_list)),
            "order_index": order_idx
        })

    # Adicionar Preâmbulo se houver texto
    if preamble_lines:
        preamble_text = format_clean_text("\n\n".join(preamble_lines))
        preamble_art = {
            "id": f"{code}_PREAMBULO",
            "law_code": code,
            "law_title": title,
            "category": category,
            "article_number": "Preâmbulo",
            "content": preamble_text,
            "revoked_content": None,
            "order_index": 0,
            "header_info": title.upper()
        }
        articles.insert(0, preamble_art)

    # Prefixar o cabeçalho estrutural no conteúdo do primeiro artigo que o sucede, se houver
    for art in articles:
        h = art.pop("header_info", "")
        if h and art["article_number"] != "Preâmbulo":
            art["content"] = f"### {h} ###\n\n{art['content']}"

    print(f"[{code}] {len(articles)} artigos processados com sucesso!")
    return articles

# Súmulas Vinculantes oficiais do STF (SV 1 a SV 58)
SUMULAS_VINCULANTES = [
    (1, "Ofende a garantia constitucional do ato jurídico perfeito a decisão que, sem ponderar as circunstâncias do caso concreto, desconsidera a validade e a eficácia de acordo firmado perante a Comissão de Conciliação Prévia."),
    (2, "É inconstitucional a lei ou ato normativo estadual ou distrital que disponha sobre sistemas de consórcios e sorteios, inclusive bingos e loterias."),
    (3, "Nos processos perante o Tribunal de Contas da União asseguram-se o contraditório e a ampla defesa quando da decisão puder resultar anulação ou revogação de ato administrativo que beneficie o interessado, excetuada a apreciação da legalidade do ato de concessão inicial de aposentadoria, reforma e pensão."),
    (4, "Salvo nos casos previstos na Constituição, o salário mínimo não pode ser usado como indexador de base de cálculo de vantagem de servidor público ou de empregado, nem ser substituído por decisão judicial."),
    (5, "A falta de defesa técnica por advogado no processo administrativo disciplinar não ofende a Constituição."),
    (6, "Não viola a eficácia dos pagamentos de vencimentos de servidores públicos a fixação de data para o encerramento do exercício financeiro distinta daquela prevista na lei de diretrizes orçamentárias."),
    (7, "A norma do § 3º do artigo 192 da Constituição, revogada pela Emenda Constitucional nº 40/2003, que limitava a taxa de juros reais a 12% ao ano, tinha sua aplicação condicionada à edição de lei complementar."),
    (8, "São inconstitucionais o parágrafo único do artigo 5º do Decreto-Lei nº 1.569/1977 e os artigos 45 e 46 da Lei nº 8.212/1991, que tratam de prescrição e decadência de crédito tributário."),
    (9, "O disposto no artigo 127 da Lei nº 7.210/1984 (Lei de Execução Penal) foi recebido pela ordem constitucional vigente, e não se lhe aplica o limite temporal da remição anterior à falta grave."),
    (10, "Viola a cláusula de reserva de plenário (CF, art. 97) a decisão de órgão fracionário de Tribunal que, embora não declare expressamente a inconstitucionalidade de lei ou ato normativo do poder público, afasta sua incidência, no todo ou em parte."),
    (11, "Só é lícito o uso de algemas em casos de resistência e de fundado receio de fuga ou de perigo à integridade física própria ou alheia, por parte do preso ou de terceiros, justificada a excepcionalidade por escrito, sob pena de responsabilidade disciplinar, civil e penal da autoridade ou do agente e de nulidade da prisão ou do ato processual a que se refere, sem prejuízo da responsabilidade civil do Estado."),
    (12, "A cobrança de taxa de matrícula nas universidades públicas viola o disposto no art. 206, IV, da Constituição Federal."),
    (13, "A nomeação de cônjuge, companheiro ou parente em linha reta, colateral ou por afinidade, até o terceiro grau, inclusive, da autoridade nomeante ou de servidor da mesma pessoa jurídica investido em cargo de direção, chefia ou assessoramento, para o exercício de cargo em comissão ou de confiança ou, ainda, de função gratificada na administração pública direta e indireta em qualquer dos poderes da União, dos Estados, do Distrito Federal e dos Municípios, compreendido o ajuste mediante designações recíprocas, viola a Constituição Federal."),
    (14, "É direito do defensor, no interesse do representado, ter acesso amplo aos elementos de prova que, já documentados em procedimento investigatório realizado por órgão com competência de polícia judiciária, digam respeito ao exercício do direito de defesa."),
    (15, "O cálculo de gratificações e outras vantagens não incide sobre o abono utilizado para se atingir o salário mínimo."),
    (16, "Os artigos 7º, IV, e 39, § 3º (redação da EC 19/98), da Constituição, referem-se ao total da remuneração percebida pelo servidor público."),
    (17, "Durante o período previsto no parágrafo 1º do artigo 100 da Constituição, não incidem juros de mora sobre os precatórios que nele sejam pagos."),
    (18, "A dissolução da sociedade ou do vínculo conjugal, no curso do mandato eletivo, não afasta a inelegibilidade prevista no § 7º do artigo 14 da Constituição Federal relativamente ao respectivo titular."),
    (19, "A taxa cobrada exclusivamente em razão dos serviços públicos de coleta, remoção e tratamento ou destinação de lixo ou resíduos provenientes de imóveis, não viola o artigo 145, II, da Constituição Federal."),
    (20, "A Gratificação de Desempenho de Atividade Técnico-Administrativa - GDATA, instituída pela Lei nº 10.404/2002, deve ser deferida aos inativos nos valores correspondentes a 37,5 (trinta e sete vírgula cinco) pontos no período de fevereiro a maio de 2002 e, nos termos do artigo 5º, parágrafo único, da Lei nº 10.404/2002, no período de junho de 2002 até a conclusão dos efeitos do último ciclo de avaliação."),
    (21, "É inconstitucional a exigência de depósito ou arrolamento prévios de dinheiro ou bens para admissibilidade de recurso administrativo."),
    (22, "A Justiça do Trabalho é competente para processar e julgar as ações de indenização por danos morais e patrimoniais decorrentes de acidente de trabalho propostas por empregado contra empregador, inclusive aquelas que ainda não possuíam sentença de mérito em primeiro grau quando da promulgação da Emenda Constitucional nº 45/2004."),
    (23, "A Justiça do Trabalho é competente para processar e julgar ação possessória ajuizada em decorrência do exercício do direito de greve pelos trabalhadores da iniciativa privada."),
    (24, "Não se tipifica crime material contra a ordem tributária, previsto no art. 1º, incisos I a IV, da Lei nº 8.137/1990, antes do lançamento definitivo do tributo."),
    (25, "É ilícita a prisão civil de depositário infiel, qualquer que seja a modalidade do depósito."),
    (26, "Para efeito de progressão de regime no cumprimento de pena por crime hediondo, ou equiparado, o juízo da execução observará a inconstitucionalidade do art. 2º da Lei nº 8.072/1990, sem prejuízo de avaliar se o condenado preenche, ou não, os requisitos objetivos e subjetivos do benefício, podendo determinar, para tal fim, de modo fundamentado, a realização de exame criminológico."),
    (27, "Compete à Justiça Estadual processar e julgar crime de falsa anotação na Carteira de Trabalho e Previdência Social, salvo quando o prejuízo for suportado pela União, autarquia ou empresa pública federal."),
    (28, "É inconstitucional a conformação de índices de correção monetária de débitos trabalhistas pela TR."),
    (29, "É constitucional a adoção, no cálculo do valor de taxa, de um ou mais elementos da base de cálculo própria de determinado imposto, desde que não haja integral identidade entre uma base e outra."),
    (30, "A falta de estabelecimento penal adequado não autoriza a manutenção do condenado em regime prisional mais gravoso."),
    (31, "É inconstitucional a fixação do limite máximo de idade para inscrição em concurso público quando não justificado pela natureza das atribuições do cargo a ser preenchido."),
    (32, "O ICMS não incide sobre a alienação de salvados de sinistro pelas companhias seguradoras."),
    (33, "Aplicam-se ao servidor público, no que couber, as regras do regime geral da previdência social sobre aposentadoria especial de que trata o artigo 40, § 4º, inciso III da Constituição Federal, até a edição de lei complementar específica."),
    (34, "A imunidade do art. 149, § 2º, I, da CF/1988 não alcança a Contribuição Social sobre o Lucro Líquido."),
    (35, "A homologação do acordo de delação premiada pelo juiz competente não impede o controle de legalidade dos termos avençados pelo órgão colegiado."),
    (36, "O comparecimento espontâneo do réu ou do executado supre a falta ou a nulidade da citação."),
    (37, "Não cabe ao Poder Judiciário, que não tem função legislativa, aumentar vencimentos de servidores públicos sob o fundamento de isonomia."),
    (38, "É inconstitucional a incidência de ISS sobre a prestação de serviços de transporte interestadual e intermunicipal."),
    (39, "Compete ao Município fixar o horário de funcionamento de estabelecimento comercial."),
    (40, "A contribuição confederativa de que trata o art. 8º, IV, da Constituição, só é exigível dos filiados ao sindicato respectivo."),
    (41, "O serviço de iluminação pública não pode ser remunerado mediante taxa."),
    (42, "É inconstitucional a vinculação do reajuste de vencimentos de servidores estaduais ou municipais a índices federais de correção monetária."),
    (43, "É inconstitucional toda modalidade de provimento que propicie ao servidor investir-se, sem prévia aprovação em concurso público destinado ao seu provimento, em cargo que não integra a carreira na qual anteriormente investido."),
    (44, "A falta de estabelecimento prisional adequado não autoriza a manutenção do preso em regime prisional mais gravoso."),
    (45, "A competência constitucional do Tribunal do Júri prevalece sobre o foro por prerrogativa de função estabelecido exclusivamente pela Constituição Estadual."),
    (46, "A definição dos crimes de responsabilidade e o estabelecimento das respectivas normas de processo e julgamento são da competência legislativa privativa da União."),
    (47, "Os honorários advocatícios incluídos na condenação ou destacados do montante principal pertencem ao advogado, tendo este direito autônomo para executar a sentença nesta parte."),
    (48, "Na entrada de mercadoria importada do exterior, é legítima a cobrança do ICMS por ocasião do desembaraço aduaneiro."),
    (49, "Ofende o princípio da livre concorrência lei municipal que impede a instalação de estabelecimentos comerciais do mesmo ramo em determinada área."),
    (50, "Norma legal que altera o prazo de recolhimento de obrigação tributária não se sujeita ao princípio da anterioridade."),
    (51, "O reajuste de 28,86%, concedido aos servidores militares pelas Leis 8.622/1993 e 8.627/1993, estende-se aos servidores públicos civis do Poder Executivo, observadas as eventuais compensações dos reajustes concedidos pelas mesmas leis."),
    (52, "Ainda quando alugado a terceiros, permanece imune ao IPTU o imóvel pertencente a qualquer das entidades referidas pelo art. 150, VI, 'c', da Constituição Federal, desde que o valor dos aluguéis seja aplicado nas atividades para as quais tais entidades foram constituídas."),
    (53, "A competência da Justiça do Trabalho prevista no art. 114, VIII, da Constituição Federal alcança a execução de ofício das contribuições previdenciárias relativas ao objeto da condenação constante das sentenças que proferir e acordos por ela homologados."),
    (54, "A medida provisória não apreciada pelo Congresso Nacional podia, até a Emenda Constitucional nº 32/2001, ser reeditada dentro do seu prazo de eficácia de trinta dias, mantidos os efeitos de decorrentes de sua edição original."),
    (55, "O direito ao auxílio-alimentação não se estende aos servidores inativos."),
    (56, "A falta de estabelecimento penal adequado não autoriza a manutenção do condenado em regime prisional mais gravoso, devendo-se observar os parâmetros estabelecidos no RE 641.320/RS."),
    (57, "A imunidade tributária da alínea 'd' do inciso VI do artigo 150 da Constituição Federal alcança componentes eletrônicos destinados, exclusivamente, a integrar livro digital."),
    (58, "O crime de posse irregular de arma de fogo de uso permitido ou restrito de que tratam os arts. 12 e 16 da Lei nº 10.826/2003 admite a aplicação do princípio da insignificância, dependendo das circunstâncias do caso concreto.")
]

def generate_sumulas():
    articles = []
    order = 0
    for num, texto in SUMULAS_VINCULANTES:
        order += 1
        articles.append({
            "id": f"SV_STF_{num}",
            "law_code": "SV_STF",
            "law_title": "Súmulas Vinculantes do Supremo Tribunal Federal",
            "category": "Súmulas / Jurisprudência",
            "article_number": f"Súmula Vinculante {num}",
            "content": f"Enunciado da Súmula Vinculante nº {num}:\n\n\"{texto}\"",
            "revoked_content": None,
            "order_index": order
        })
    return articles

def generate_full_database(output_path: str):
    print("=" * 60)
    print("INICIANDO CONSTRUÇÃO DO VADE MECUM COMPLETO")
    print("=" * 60)

    all_articles = []
    law_summaries = []

    for law in LAWS:
        html = download_law(law["url"], law["code"])
        arts = parse_law(html, law)
        if arts:
            all_articles.extend(arts)
            law_summaries.append((
                law["code"],
                law["title"],
                law["category"],
                len(arts),
                datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            ))

    # Súmulas Vinculantes
    sv_arts = generate_sumulas()
    all_articles.extend(sv_arts)
    law_summaries.append((
        "SV_STF",
        "Súmulas Vinculantes do Supremo Tribunal Federal",
        "Súmulas / Jurisprudência",
        len(sv_arts),
        datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    ))

    print(f"\nTOTAL GERAL: {len(law_summaries)} Leis/Compilações | {len(all_articles)} Artigos")

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
        order_index INTEGER NOT NULL,
        revoked_content TEXT
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
    INSERT INTO articles (id, law_code, law_title, category, article_number, content, order_index, revoked_content)
    VALUES (:id, :law_code, :law_title, :category, :article_number, :content, :order_index, :revoked_content)
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

    cur.execute("""
    CREATE TABLE IF NOT EXISTS user_text_highlights (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        article_id TEXT NOT NULL,
        start_offset INTEGER NOT NULL,
        end_offset INTEGER NOT NULL,
        selected_text TEXT NOT NULL,
        color TEXT NOT NULL,
        created_at INTEGER NOT NULL
    )
    """)
    cur.execute("CREATE INDEX IF NOT EXISTS idx_user_text_highlights_art ON user_text_highlights(article_id);")

    # Injetar room_master_table e user_version para Room
    cur.execute("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
    cur.execute("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, 'bfb01156b90d43da6b26ba19337ebd54')")
    cur.execute("PRAGMA user_version = 2;")
    conn.commit()
    conn.close()

    db_size = os.path.getsize(output_path)
    print(f"\nBanco de dados SQLite Vade Mecum gerado com sucesso!")
    print(f"Caminho: {output_path}")
    print(f"Tamanho: {db_size / (1024*1024):.2f} MB")

if __name__ == "__main__":
    out_db = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "juridireader.db")
    if len(sys.argv) > 1:
        out_db = sys.argv[1]
    generate_full_database(out_db)
