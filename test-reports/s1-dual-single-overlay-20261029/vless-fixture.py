import socket, threading, select, uuid, json, datetime
from pathlib import Path
ROOT=Path(__file__).parent
LOG=ROOT/'vless-fixture.jsonl'
TOKEN=uuid.UUID('d6f72d12-3989-4260-a438-8b45b1a96f12').bytes
lock=threading.Lock()
def log(**data):
    data['time']=datetime.datetime.now().isoformat()
    with lock:
        with LOG.open('a',encoding='utf8') as f: f.write(json.dumps(data)+chr(10))
    print(json.dumps(data),flush=True)
def exact(c,n):
    out=bytes()
    while len(out)<n:
        b=c.recv(n-len(out))
        if not b: raise EOFError()
        out+=b
    return out
def client(c):
    up=None
    try:
        c.settimeout(20)
        version=exact(c,1)
        if exact(c,16)!=TOKEN: raise ValueError('invalid test UUID')
        exact(c,exact(c,1)[0])
        command=exact(c,1)[0]
        port=int.from_bytes(exact(c,2),'big')
        kind=exact(c,1)[0]
        if kind==1: host=socket.inet_ntop(socket.AF_INET,exact(c,4))
        elif kind==2: host=exact(c,exact(c,1)[0]).decode()
        elif kind==3: host=socket.inet_ntop(socket.AF_INET6,exact(c,16))
        else: raise ValueError('address type')
        if command!=1: raise ValueError('fixture supports TCP only')
        up=socket.create_connection((host,port),timeout=15)
        c.sendall(version+bytes([0]))
        log(event='connected',host=host,port=port)
        c.settimeout(None);up.settimeout(None)
        while True:
            ready,_,_=select.select([c,up],[],[],30)
            if not ready: break
            for src in ready:
                data=src.recv(65536)
                if not data: return
                dst=up if src is c else c
                dst.sendall(data)
    except Exception as e: log(event='error',error=type(e).__name__,detail=str(e))
    finally:
        c.close()
        if up: up.close()
s=socket.socket();s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);s.bind(('127.0.0.1',18443));s.listen()
log(event='listening',address='127.0.0.1',port=18443)
while True:
    c,_=s.accept();threading.Thread(target=client,args=(c,),daemon=True).start()


