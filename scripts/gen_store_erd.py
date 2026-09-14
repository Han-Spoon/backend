# -*- coding: utf-8 -*-
"""가게 도메인 ERD SVG 생성기 — 속성 5열(Key/논리명/물리명/타입/Nullable)"""
from xml.sax.saxutils import escape as esc

HDR_H, CHDR_H, ROW_H, PAD_B = 34, 21, 19, 12
TW = 450                                   # 테이블 폭
CX = (13, 46, 168, 312, 436)               # key, 논리, 물리, 타입, null(우측정렬)
BADGE_C = {'PK':'#b25a12','FK':'#1e5aa8','UQ':'#2f7a5a','GEN':'#6b4ea8'}
KIND = {'ref':('#1e5aa8','pnl-ref'), 'core':('#b25a12','pnl-core'), 'sat':('#2f7a5a','pnl-sat'),
        'exist':('#5a616b','pnl-exist'), 'ai':('#6b4ea8','pnl-ai')}
out, R = [], {}

def table(x, y, name, rows, kind, note=''):
    hdr, _ = KIND[kind]
    h = HDR_H + CHDR_H + ROW_H*len(rows) + PAD_B + (16 if note else 0)
    out.append(f'<g><rect class="tbl" x="{x}" y="{y}" width="{TW}" height="{h}" rx="7"/>')
    out.append(f'<path d="M{x} {y+10} a10 10 0 0 1 10 -10 h{TW-20} a10 10 0 0 1 10 10 v{HDR_H-10} h-{TW} z" fill="{hdr}"/>')
    out.append(f'<text class="tname" x="{x+13}" y="{y+23}">{esc(name)}</text>')
    ch = y + HDR_H
    out.append(f'<rect class="chdr" x="{x+1}" y="{ch}" width="{TW-2}" height="{CHDR_H}"/>')
    for lbl, off, anc in (('KEY',CX[0],'start'),('논리명',CX[1],'start'),('물리명',CX[2],'start'),
                          ('데이터타입',CX[3],'start'),('NULL',CX[4],'end')):
        out.append(f'<text class="chl" x="{x+off}" y="{ch+14}" text-anchor="{anc}">{lbl}</text>')
    out.append(f'<line class="sec" x1="{x+1}" y1="{ch+CHDR_H}" x2="{x+TW-1}" y2="{ch+CHDR_H}"/>')
    ty = y + HDR_H + CHDR_H + 14
    for r in rows:
        if r[0] == 'SEC':
            out.append(f'<line class="sec" x1="{x+11}" y1="{ty-5}" x2="{x+TW-11}" y2="{ty-5}"/>')
            out.append(f'<text class="secl" x="{x+13}" y="{ty+3}">{esc(r[1])}</text>')
        else:
            k, lo, ph, dt, nu = r
            if k:
                out.append(f'<text class="badge" x="{x+CX[0]}" y="{ty+3}" fill="{BADGE_C[k]}">{k}</text>')
            out.append(f'<text class="lo" x="{x+CX[1]}" y="{ty+3}">{esc(lo)}</text>')
            out.append(f'<text class="ph" x="{x+CX[2]}" y="{ty+3}">{esc(ph)}</text>')
            out.append(f'<text class="dt" x="{x+CX[3]}" y="{ty+3}">{esc(dt)}</text>')
            cls = 'nuY' if nu == 'Y' else 'nuN'
            out.append(f'<text class="{cls}" x="{x+CX[4]}" y="{ty+3}" text-anchor="end">{nu}</text>')
        ty += ROW_H
    if note:
        out.append(f'<text class="tnote" x="{x+13}" y="{y+h-10}">{esc(note)}</text>')
    out.append('</g>')
    R[name] = (x, y, TW, h)
    return h

def panel(x, y, w, h, title, kind, dashed=False):
    _, cls = KIND[kind]
    d = ' stroke-dasharray="7 5"' if dashed else ''
    out.append(f'<rect class="pnl {cls}" x="{x}" y="{y}" width="{w}" height="{h}" rx="12"{d}/>')
    out.append(f'<text class="ptitle" x="{x+18}" y="{y+27}">{esc(title)}</text>')

# 앵커 헬퍼 — 커넥터 좌표를 rect에서 계산
def ry(n, i): x,y,w,h = R[n]; return y + HDR_H + CHDR_H + 14 + ROW_H*i - 1
def rt(n):    x,y,w,h = R[n]; return y
def rb(n):    x,y,w,h = R[n]; return y + h
def rl(n):    return R[n][0]
def rr(n):    return R[n][0] + TW

W, H = 2130, 1490
TOP_Y, TOP_H, BOT_Y, BOT_H = 112, 680, 830, 546
panel(40,   TOP_Y, 490, TOP_H, '참조 마스터 · BIGINT PK',  'ref')
panel(560,  TOP_Y, 490, TOP_H, '가게 마스터 · V5 예정 (미적용)',        'core')
panel(1080, TOP_Y, 490, TOP_H, '별칭 · 외부참조',            'sat')
panel(40,   BOT_Y, 1530, BOT_H, '기존 백엔드 스키마 · UUID PK (V1 배포됨)', 'exist')
panel(1600, TOP_Y, 490, 1264, 'AI 연계 스키마 (catoin) · 백엔드 소유, 미구현', 'ai', dashed=True)

y = TOP_Y + 50
y += table(60, y, 'store_categories', [
  ('PK','분류 식별자','id','BIGINT','N'),
  ('UQ','업종분류 코드','code','VARCHAR(6)','N'),
  ('','분류명','name','VARCHAR(60)','N'),
  ('','분류 단계','level','SMALLINT','N'),
  ('FK','상위 분류','parent_id','BIGINT','Y'),
], 'ref', '247개 전체 적재 · store_cluster 축') + 22
y += table(60, y, 'ksic_codes', [
  ('PK','표준산업분류 코드','code','VARCHAR(6)','N'),
  ('','분류명','name','VARCHAR(120)','N'),
], 'ref') + 22
table(60, y, 'store_import_batches', [
  ('PK','적재 배치 식별자','id','BIGINT','N'),
  ('UQ','원천 구분','source','VARCHAR(20)','N'),
  ('UQ','원천 버전','source_version','VARCHAR(10)','N'),
  ('','적재 건수','row_count','INTEGER','N'),
  ('','처리 상태','status','VARCHAR(20)','N'),
  ('','시작 시각','started_at','TIMESTAMPTZ','N'),
  ('','종료 시각','finished_at','TIMESTAMPTZ','Y'),
], 'ref', '분기 upsert 감사 · 롤백 기준점')

table(580, TOP_Y+50, 'stores', [
  ('PK','가게 식별자','id','BIGINT','N'),
  ('UQ','상가업소번호','sbiz_store_no','VARCHAR(24)','Y'),
  ('SEC','표시 · 매칭'),
  ('','상호명','name','VARCHAR(200)','N'),
  ('','지점명','branch_name','VARCHAR(100)','N'),
  ('GEN','정규화 상호명','name_normalized','VARCHAR(200)','N'),
  ('SEC','분류'),
  ('FK','업종 소분류','category_id','BIGINT','Y'),
  ('FK','표준산업분류','ksic_code','VARCHAR(6)','Y'),
  ('SEC','위치'),
  ('','행정동 코드','admin_dong_code','VARCHAR(8)','N'),
  ('','도로명주소','road_address','VARCHAR(300)','N'),
  ('','층 정보','floor_info','VARCHAR(20)','N'),
  ('','위도','lat','DOUBLE PREC.','N'),
  ('','경도','lng','DOUBLE PREC.','N'),
  ('SEC','상태 · 출처'),
  ('','영업 상태','status','VARCHAR(20)','N'),
  ('','레코드 출처','origin','VARCHAR(20)','N'),
  ('','비활성 시각','inactive_at','TIMESTAMPTZ','Y'),
  ('','검증 완료 시각','verified_at','TIMESTAMPTZ','Y'),
  ('FK','제출 사용자','submitted_by','UUID','Y'),
  ('FK','최종 적재 배치','last_batch_id','BIGINT','Y'),
  ('SEC','감사'),
  ('','생성 시각','created_at','TIMESTAMPTZ','N'),
  ('','수정 시각','updated_at','TIMESTAMPTZ','N'),
], 'core', '359,832건 · 156MB · 반경검색 실측 1.87ms')

y = TOP_Y + 50
y += table(1100, y, 'store_aliases', [
  ('PK','별칭 식별자','id','BIGINT','N'),
  ('FK','가게','store_id','BIGINT','N'),
  ('','별칭','alias','VARCHAR(200)','N'),
  ('GEN','정규화 별칭','alias_normalized','VARCHAR(200)','N'),
  ('','별칭 출처','source','VARCHAR(20)','N'),
], 'sat', '가게별 별칭 (사용자 제보)') + 22
y += table(1100, y, 'brand_aliases', [
  ('PK','변형 식별자','id','BIGINT','N'),
  ('UQ','정규화 변형표기','variant_normalized','VARCHAR(200)','N'),
  ('','정규화 대표표기','canonical_normalized','VARCHAR(200)','N'),
  ('','비고','note','VARCHAR(200)','N'),
], 'sat', '전역 표기 변형 · store FK 없음 (앱 레벨 치환)') + 22
table(1100, y, 'store_external_refs', [
  ('PK','참조 식별자','id','BIGINT','N'),
  ('FK','가게','store_id','BIGINT','N'),
  ('UQ','제공자','provider','VARCHAR(20)','N'),
  ('UQ','외부 장소 ID','external_id','VARCHAR(64)','N'),
  ('','외부 장소 URL','external_url','VARCHAR(512)','N'),
  ('','연결 시각','linked_at','TIMESTAMPTZ','N'),
], 'sat', '⚠ 약관: ID/URL만 저장. 상호·주소·좌표 금지')

table(60, BOT_Y+50, 'users', [
  ('PK','사용자 식별자','id','UUID','N'),
  ('UQ','이메일','email','VARCHAR(255)','N'),
  ('','닉네임','nickname','VARCHAR(100)','N'),
  ('','표시 언어','language_code','VARCHAR(2)','N'),
], 'exist')

table(580, BOT_Y+50, 'scan_sessions', [
  ('PK','스캔 식별자','id','UUID','N'),
  ('FK','사용자','user_id','UUID','N'),
  ('SEC','V5 가게 연결 (미적용)'),
  ('FK','가게','store_id','BIGINT','Y'),
  ('','가게명 스냅샷','store_name_snapshot','VARCHAR(200)','Y'),
  ('','가게 매칭 방식','store_match_method','VARCHAR(20)','Y'),
  ('SEC','V2 스캔 멱등 · 실패코드'),
  ('UQ','S3 객체 키','storage_key','VARCHAR(512)','Y'),
  ('','실패 코드','failure_code','VARCHAR(64)','Y'),
  ('','낙관적 락','lock_version','BIGINT','N'),
  ('SEC','기존 · V3 재촬영 안내'),
  ('','스캔 상태','scan_status','VARCHAR(20)','N'),
  ('','메뉴 수','menu_count','INTEGER','Y'),
  ('','스캔 시각','scanned_at','TIMESTAMPTZ','Y'),
  ('','재촬영 사유','retake_reasons','JSONB','Y'),
  ('','재촬영 제안','retake_suggestions','JSONB','Y'),
], 'exist', 'store context 3개 all-or-none · 사용자 GPS 원본 없음')

yy = BOT_Y + 50
yy += table(1100, yy, 'menu_images', [
  ('PK','이미지 식별자','id','UUID','N'),
  ('FK','스캔 세션','scan_session_id','UUID','N'),
  ('','이미지 소스','source','VARCHAR(20)','Y'),
  ('','저장 키','storage_key','VARCHAR(512)','Y'),
  ('','S3 객체 버전','object_version_id','VARCHAR(1024)','Y'),
  ('','S3 ETag','etag','VARCHAR(255)','Y'),
], 'exist', 'V4: OCR 분석 대상 객체 동일성 검증') + 22
table(1100, yy, 'menu_analyses', [
  ('PK','분석 식별자','id','UUID','N'),
  ('FK','스캔 세션','scan_session_id','UUID','N'),
  ('','표시 순서','display_order','INTEGER','Y'),
  ('','메뉴명(한/영)','menu_name_ko/_en','VARCHAR(255)','Y'),
  ('','설명(한/영)','description_ko/_en','TEXT','Y'),
  ('','가격 텍스트','price_text','VARCHAR(100)','Y'),
  ('','위험도','risk_level','VARCHAR(20)','Y'),
  ('','히트 태그','hit_tags','JSONB','Y'),
  ('','다국어 안내문','message','JSONB','Y'),
  ('','사장님 카드','owner_card','JSONB','Y'),
], 'exist')

y = TOP_Y + 50
y += table(1620, y, 'store_menus', [
  ('FK','가게','store_id','BIGINT','N'),
  ('FK','메뉴','menu_id','BIGINT','N'),
  ('','최초 확인 시각','first_seen_at','TIMESTAMPTZ','N'),
], 'ai', '가게 ↔ 메뉴 연결점') + 22
y += table(1620, y, 'ingredient_risk_scores', [
  ('FK','가게','store_id','BIGINT','N'),
  ('FK','메뉴','menu_id','BIGINT','N'),
  ('FK','재료','ingredient_id','BIGINT','N'),
  ('','알파','alpha','DOUBLE PREC.','N'),
  ('','베타','beta','DOUBLE PREC.','N'),
], 'ai', 'store_cluster fallback 대상') + 22
y += table(1620, y, 'ingredient_confirmations', [
  ('FK','가게','store_id','BIGINT','N'),
  ('FK','메뉴','menu_id','BIGINT','N'),
  ('FK','재료','ingredient_id','BIGINT','N'),
  ('','존재 여부','present','BOOLEAN','N'),
  ('','이상 플래그','flagged_anomaly','BOOLEAN','N'),
], 'ai', 'hard evidence override') + 22
y += table(1620, y, 'ingredient_evidence_log', [
  ('PK','증거 식별자','id','BIGINT','N'),
  ('FK','가게','store_id','BIGINT','N'),
  ('','알파 증분','delta_alpha','DOUBLE PREC.','N'),
  ('','증거 출처','source_type','VARCHAR(20)','N'),
  ('','생성 시각','created_at','TIMESTAMPTZ','N'),
], 'ai', '시계열 · 파티셔닝 후보') + 22
table(1620, y, 'owner_verification_requests', [
  ('PK','질의 식별자','id','BIGINT','N'),
  ('FK','가게','store_id','BIGINT','N'),
  ('FK','스캔 세션','scan_session_id','UUID','N'),
  ('','답변 내용','answer_text','TEXT','Y'),
], 'ai')

# ══ 커넥터 ══
def link(d, dashed=False):
    da = ' stroke-dasharray="6 4"' if dashed else ''
    out.append(f'<path class="ln" d="{d}" fill="none" marker-end="url(#arrow)"{da}/>')
def lab(t, x, y): out.append(f'<text class="lbl" x="{x}" y="{y}">{esc(t)}</text>')

SC, KS, IB, ST = 'store_categories','ksic_codes','store_import_batches','stores'
AL, EX, US, SS = 'store_aliases','store_external_refs','users','scan_sessions'
MI, MA, SM, IRS = 'menu_images','menu_analyses','store_menus','ingredient_risk_scores'

link(f'M {rr(SC)} {ry(SC,0)} H 534 V {ry(ST,7)} H {rl(ST)}');  lab('1:N', 516, ry(SC,0)-6)
link(f'M {rl(SC)} {ry(SC,4)} H 49 V {ry(SC,0)} H {rl(SC)}')      # 자기참조 상위분류
lab('self', 26, (ry(SC,0)+ry(SC,4))//2)
link(f'M {rr(KS)} {ry(KS,0)} H 542 V {ry(ST,8)} H {rl(ST)}')
link(f'M {rr(IB)} {ry(IB,0)} H 550 V {ry(ST,21)} H {rl(ST)}')
link(f'M {rr(ST)} {ry(ST,0)} H 1065 V {ry(EX,1)} H {rl(EX)}'); lab('1:N', 1036, ry(ST,0)-6)
link(f'M 1065 {ry(AL,1)} H {rl(AL)}')
link(f'M 640 {rb(ST)} V 812 H 285 V {rt(US)}');                lab('submitted_by', 300, 806)
link(f'M 805 {rb(ST)} V {rt(SS)}');                            lab('1:N (NULL 허용)', 813, 790)
link(f'M {rr(US)} {ry(US,0)} H 545 V {ry(SS,1)} H {rl(SS)}');  lab('1:N', 516, ry(US,0)-6)
link(f'M {rr(SS)} {ry(SS,0)} H 1065 V {ry(MI,1)} H {rl(MI)}'); lab('1:1', 1036, ry(SS,0)-6)
link(f'M 1065 {ry(MI,1)} V {ry(MA,1)} H {rl(MA)}');            lab('1:N', 1036, ry(MA,1)-6)
link(f'M 805 {rt(ST)} V 96 H 1585 V {ry(IRS,0)} H {rl(IRS)}')
link(f'M 1585 {ry(SM,0)} H {rl(SM)}')
out.append('<text class="buslbl" x="821" y="90">store_id — 모든 store-scoped 테이블에 NOT NULL 강제 (agent-1 §0-1)</text>')

for i, t in enumerate(['⚠ 이 패널 5개 테이블은 아직 구현되지 않았다.',
                       '물리 스키마와 마이그레이션은 백엔드가 단일 소유한다.',
                       'AI는 구조화된 결과/저장 명령만 반환하고 백엔드가',
                       '권한·FK·멱등성을 검증한 뒤 트랜잭션으로 반영한다.',
                       '',
                       'store_id 계약: PostgreSQL BIGINT · Java Long ·',
                       'JSON 정수 · Python int. AI의 stores 직접 쓰기 금지.']):
    out.append(f'<text class="pnote" x="1620" y="{1100+i*18}">{esc(t)}</text>')

# ══ 범례 ══
LY = 1436
out.append(f'<rect class="pnl pnl-exist" x="40" y="{LY-30}" width="2050" height="66" rx="10"/>')
out.append(f'<text class="lgdh" x="62" y="{LY-10}">속성 구성: KEY · 논리명 · 물리명 · 데이터타입 · NULL</text>')
lx = 62
for b, t in [('PK','기본키'),('FK','외래키'),('UQ','유니크'),('GEN','생성 컬럼')]:
    out.append(f'<text class="badge" x="{lx}" y="{LY+14}" fill="{BADGE_C[b]}">{b}</text>')
    out.append(f'<text class="lgd" x="{lx+32}" y="{LY+14}">{esc(t)}</text>')
    lx += 122
out.append(f'<text class="nuY" x="{lx}" y="{LY+14}">Y</text>')
out.append(f'<text class="lgd" x="{lx+16}" y="{LY+14}">NULL 허용</text>')
lx += 110
out.append(f'<text class="nuN" x="{lx}" y="{LY+14}">N</text>')
out.append(f'<text class="lgd" x="{lx+16}" y="{LY+14}">NOT NULL</text>')
lx += 118
out.append(f'<line class="ln" x1="{lx}" y1="{LY+10}" x2="{lx+32}" y2="{LY+10}"/>')
out.append(f'<text class="lgd" x="{lx+40}" y="{LY+14}">DB FK 제약</text>')
lx += 150
for cx, cls, t in [(lx,'pnl-ref','참조 마스터'),(lx+140,'pnl-core','신규 핵심'),(lx+262,'pnl-sat','부속'),
                   (lx+352,'pnl-exist','기존 V1'),(lx+462,'pnl-ai','AI 연계')]:
    out.append(f'<rect class="pnl {cls}" x="{cx}" y="{LY+2}" width="14" height="14" rx="3"/>')
    out.append(f'<text class="lgd" x="{cx+20}" y="{LY+14}">{esc(t)}</text>')
out.append(f'<text class="lgdn" x="{lx+600}" y="{LY+14}">PK 이원화: 공개 마스터=BIGINT IDENTITY(좁은 FK·순차 적재) / 사용자 귀속 리소스=UUID(URL 열거 방어)</text>')

SVG = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" font-family="-apple-system, BlinkMacSystemFont, 'Apple SD Gothic Neo', 'Malgun Gothic', 'Noto Sans KR', system-ui, sans-serif">
<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10 z" class="mk"/></marker></defs>
<style>
 .bg{{fill:#fbfcfd}} .pnl{{stroke-width:1.2}} .tbl{{fill:#fff;stroke:#dbe0e6;stroke-width:1}}
 .chdr{{fill:#f6f8fa}}
 .pnl-ref{{fill:#eaf1fa;stroke:#b3cce8}} .pnl-core{{fill:#fdf3e7;stroke:#eec79a}}
 .pnl-sat{{fill:#eaf6f0;stroke:#aedac6}} .pnl-exist{{fill:#f1f3f5;stroke:#d2d7dd}}
 .pnl-ai{{fill:#f3eefa;stroke:#c9bce6}}
 .title{{font-size:25px;font-weight:700;fill:#12151a}} .sub{{font-size:13px;fill:#666f7a}}
 .ptitle{{font-size:13.5px;font-weight:700;fill:#3c444e}} .tname{{font-size:14px;font-weight:700;fill:#fff}}
 .chl{{font-size:8.5px;font-weight:700;fill:#98a0aa;letter-spacing:.5px}}
 .lo{{font-size:11.5px;fill:#1c2026}}
 .ph{{font-size:11.5px;fill:#1c2026;font-family:ui-monospace,SFMono-Regular,Menlo,monospace}}
 .dt{{font-size:10.5px;fill:#7b838d;font-family:ui-monospace,SFMono-Regular,Menlo,monospace}}
 .nuN{{font-size:10.5px;font-weight:700;fill:#b0562c}} .nuY{{font-size:10.5px;fill:#9aa3ad}}
 .badge{{font-size:9px;font-weight:700;letter-spacing:.3px}}
 .secl{{font-size:9.5px;font-weight:700;fill:#98a0aa;letter-spacing:.4px}} .sec{{stroke:#e8ebef;stroke-width:1}}
 .tnote{{font-size:10px;fill:#7b838d;font-style:italic}} .pnote{{font-size:10.5px;fill:#7b838d}}
 .ln{{stroke:#7d8792;stroke-width:1.4}} .mk{{fill:#7d8792}}
 .lbl{{font-size:10px;fill:#6b7480}} .lgd{{font-size:11.5px;fill:#3c444e}}
 .lgdh{{font-size:11.5px;font-weight:700;fill:#3c444e}} .lgdn{{font-size:11px;fill:#6b7480}}
 .buslbl{{font-size:11px;font-weight:600;fill:#8a6a2a}}
 @media (prefers-color-scheme: dark) {{
  .bg{{fill:#12151a}} .tbl{{fill:#1c2128;stroke:#333b45}} .chdr{{fill:#22282f}}
  .pnl-ref{{fill:#141c28;stroke:#2c4460}} .pnl-core{{fill:#241c12;stroke:#5e4526}}
  .pnl-sat{{fill:#12211b;stroke:#2a4d3c}} .pnl-exist{{fill:#181b20;stroke:#333b45}}
  .pnl-ai{{fill:#1d1826;stroke:#453a5e}}
  .title{{fill:#eef1f5}} .sub{{fill:#9aa3ad}} .ptitle{{fill:#c3cad2}} .chl{{fill:#767e88}}
  .lo{{fill:#e2e6eb}} .ph{{fill:#e2e6eb}} .dt{{fill:#8d959f}} .nuN{{fill:#e08a5a}} .nuY{{fill:#767e88}}
  .secl{{fill:#767e88}} .sec{{stroke:#2b323a}} .tnote{{fill:#8d959f}} .pnote{{fill:#8d959f}}
  .ln{{stroke:#8d959f}} .mk{{fill:#8d959f}} .lbl{{fill:#9aa3ad}} .lgd{{fill:#c3cad2}}
  .lgdh{{fill:#c3cad2}} .lgdn{{fill:#9aa3ad}} .buslbl{{fill:#d8a24e}}
 }}
</style>
<rect class="bg" x="0" y="0" width="{W}" height="{H}"/>
<text class="title" x="40" y="46">한스푼 가게 도메인 ERD — V2</text>
<text class="sub" x="40" y="70">상가정보 한식 359,832건 마스터 · 카카오 place_id 연결 · 상호명+좌표 동시 매칭 (실측 1.87ms / 156MB)</text>
{chr(10).join(out)}
</svg>'''

import io, sys
io.open(sys.argv[1],'w',encoding='utf-8').write(SVG)

bad, items = [], list(R.items())
for i in range(len(items)):
    for j in range(i+1, len(items)):
        (n1,(x1,y1,w1,h1)), (n2,(x2,y2,w2,h2)) = items[i], items[j]
        if x1 < x2+w2 and x2 < x1+w1 and y1 < y2+h2 and y2 < y1+h1: bad.append(f'겹침 {n1}×{n2}')
    n,(x,y,w,h) = items[i]
    if x+w > W or y+h > H: bad.append(f'캔버스 초과 {n}')
PANELS = [(40,TOP_Y,490,TOP_H),(560,TOP_Y,490,TOP_H),(1080,TOP_Y,490,TOP_H),(40,BOT_Y,1530,BOT_H),(1600,TOP_Y,490,1076)]
for n,(x,y,w,h) in items:
    if not any(px<=x and py<=y and x+w<=px+pw and y+h<=py+ph for px,py,pw,ph in PANELS):
        bad.append(f'패널 이탈 {n}')
print(f'테이블 {len(R)}개 · 검증 ' + ('실패: '+'; '.join(bad) if bad else '통과 ✓'), file=sys.stderr)
print('written', sys.argv[1], len(SVG), 'bytes')
