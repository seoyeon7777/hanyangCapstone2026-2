"""점수 방식 비교용 시험 스크립트 (운영 코드 아님): 현재 / 그룹 묶기 / 회피 감점 / 둘 다. python tools/score_variants.py"""
import sys,json,collections,statistics as st
import os;ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)));sys.path.insert(0,ROOT);sys.path.insert(0,ROOT+'/tests')
import matching as M,test_sweep as ts
R=M.load_rules(); T=M.TYPES
span=R.w_max-R.w_min; neutral=(0-R.w_min)/span
def grp(g):
    p=g.split(':')[0]; return 'fit' if p=='item' else p
def score(tags,prof,group=False,kappa=0.0,k=1):
    prof=M.normalize_profile(prof); known=[g for g in dict.fromkeys(tags) if g in R.weights]
    if not known: return neutral
    val=lambda g:sum(prof['ratios'][t]*R.weights[g][t] for t in T)
    if group:
        d=collections.defaultdict(list)
        for g in known: d[grp(g)].append(val(g))
        vs=[sum(v)/len(v) for v in d.values()]
    else: vs=[val(g) for g in known]
    n=len(vs); raw=sum(vs)/n; s=(raw-R.w_min)/span
    s=neutral+(s-neutral)*n/(n+k)
    s-=kappa*sum(max(0,-v) for v in vs)/span
    return min(1,max(0,s))
V={'현재':dict(),'그룹묶기':dict(group=True),'감점':dict(kappa=.5),'그룹+감점':dict(group=True,kappa=.5)}
fx=json.load(open('tests/fixtures/a_profiles.json')) if False else json.load(open(ROOT+'/tests/fixtures/a_profiles.json'))
P={k:v['result'] for k,v in fx.items() if not k.startswith('_')}
print("== 예시 (pure_S)")
ex=[["fit:slim"],["fit:slim","mat:cotton"],["fit:slim","mat:cotton","mat:wool"],["fit:slim","item:tight_skirt","sil:IH"],["fit:slim","det:ruffle"],["fit:slim","det:ruffle","len:long"],["mat:cotton"],[]]
print(f"{'태그':50}"+"".join(f"{v:>10}" for v in V))
for t in ex: print(f"{str(t):50}"+"".join(f"{score(t,P['pure_S'],**kw):10.3f}" for kw in V.values()))
items=ts.items.__wrapped__()
real=json.load(open(ROOT+'/samples/real_50_tags_20261012.json',encoding='utf-8'))['items']
sets={'격자588':[i['tags'] for i in items],'실제50':[v['tags'] for v in real.values()]}
def neg(tags,pr): return any(g in R.weights and R.weights[g][pr['primary']]<0 for g in tags)
def spear(a,b):
    ra={i:r for r,i in enumerate(sorted(range(len(a)),key=lambda i:(-a[i],i)))}
    rb={i:r for r,i in enumerate(sorted(range(len(b)),key=lambda i:(-b[i],i)))}
    n=len(a); d=sum((ra[i]-rb[i])**2 for i in range(n)); return 1-6*d/(n*(n*n-1))
for name,cat in sets.items():
    print(f"\n== {name} ({len(cat)}개)  상위10 안 '피해야 할 태그 상품' 수 / 현재와 상위10 겹침 / 순위상관")
    for pn,pr in P.items():
        row=[]; base=[score(t,pr) for t in cat]
        btop=set(sorted(range(len(cat)),key=lambda i:-base[i])[:10])
        for vn,kw in V.items():
            s=[score(t,pr,**kw) for t in cat]; top=sorted(range(len(cat)),key=lambda i:-s[i])[:10]
            row.append(f"{vn}:{sum(neg(cat[i],pr) for i in top)}/{len(btop&set(top))}/{spear(base,s):.2f}")
        print(f"{pn:7}"," | ".join(row))
# 무정보 상품 위치
print("\n== 실제50: 무정보(가중치 태그 0개) 상품의 평균 순위 백분위(0=최상위) 와, 피해야 할 태그가 있는 상품의 평균 백분위")
cat=sets['실제50']
for pn,pr in P.items():
    out=[]
    for vn,kw in V.items():
        s=[score(t,pr,**kw) for t in cat]; order=sorted(range(50),key=lambda i:-s[i]); pos={i:r/49 for r,i in enumerate(order)}
        z=[pos[i] for i,t in enumerate(cat) if not [g for g in t if g in R.weights]]; b=[pos[i] for i,t in enumerate(cat) if neg(t,pr)]
        out.append(f"{vn}: 무정보 {st.mean(z):.2f} / 회피 {st.mean(b) if b else float('nan'):.2f}")
    print(f"{pn:7}"," | ".join(out))
print("\n== 감점 방식에서 0점으로 잘리는 상품 수 (동점 발생)")
for name,cat in sets.items():
    for pn in ('pure_S','pure_W','pure_N','S_W'):
        z={vn:sum(score(t,P[pn],**kw)==0 for t in cat) for vn,kw in V.items()}
        print(name,pn,z)
print("\n== 감점 강도(kappa) 민감도: 실제50, 상위10 안 회피 상품 수 / 현재와 겹침")
for pn in ('S_W','pure_S','pure_W','W_N'):
    out=[]
    base=[score(t,P[pn]) for t in sets['실제50']]; bt=set(sorted(range(50),key=lambda i:-base[i])[:10])
    for kp in (0,.25,.5,1):
        s=[score(t,P[pn],group=True,kappa=kp) for t in sets['실제50']]; top=sorted(range(50),key=lambda i:-s[i])[:10]
        out.append(f"κ={kp}: {sum(neg(sets['실제50'][i],P[pn]) for i in top)}/{len(bt&set(top))}")
    print(pn," | ".join(out))
