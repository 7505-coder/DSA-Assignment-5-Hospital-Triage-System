import java.util.*;
import java.io.*;

class Patient {
    int id; String name; int age; int severity;
    public Patient(int id, String name, int age, int severity){
        this.id=id; this.name=name; this.age=age; this.severity=severity;
    }
    public String toString(){ return String.format("Patient[id=%d,name=%s,age=%d,severity=%d]", id,name,age,severity); }
}

class Token {
    int tokenId, patientId, doctorId, slotId;
    String type; // "ROUTINE" or "EMERGENCY"
    public Token(int tokenId,int pid,int did,int sid,String type){
        this.tokenId=tokenId; this.patientId=pid; this.doctorId=did; this.slotId=sid; this.type=type;
    }
    public String toString(){ return String.format("Token[id=%d,pid=%d,did=%d,slot=%d,type=%s]", tokenId,patientId,doctorId,slotId,type); }
}

class SlotNode {
    int slotId; String start, end; boolean booked; SlotNode next;
    public SlotNode(int slotId, String start, String end){
        this.slotId=slotId; this.start=start; this.end=end; this.booked=false; this.next=null;
    }
    public String toString(){ return String.format("Slot[id=%d %s-%s booked=%b]", slotId,start,end,booked); }
}

class Doctor {
    int id; String name; String specialization; SlotNode head;
    public Doctor(int id,String name,String spec){
        this.id=id; this.name=name; this.specialization=spec; this.head=null;
    }
    public void addSlot(int slotId,String start,String end){
        SlotNode node=new SlotNode(slotId,start,end);
        node.next=head; head=node;
    }
    public boolean cancelSlot(int slotId){
        SlotNode cur=head, prev=null;
        while(cur!=null){
            if(cur.slotId==slotId){
                if(prev==null) head=cur.next; else prev.next=cur.next;
                return true;
            }
            prev=cur; cur=cur.next;
        }
        return false;
    }
    public SlotNode findNextFree(){
        SlotNode cur=head;
        while(cur!=null){
            if(!cur.booked) return cur;
            cur=cur.next;
        }
        return null;
    }
    public String toString(){ return String.format("Doctor[id=%d,name=%s,spec=%s]", id,name,specialization); }
}

class CircularQueue {
    Token[] arr; int front, rear, size, capacity;
    public CircularQueue(int cap){
        capacity=cap; arr=new Token[cap]; front=0; rear=-1; size=0;
    }
    public boolean isFull(){ return size==capacity; }
    public boolean isEmpty(){ return size==0; }
    public boolean enqueue(Token t){
        if(isFull()) return false;
        rear=(rear+1)%capacity; arr[rear]=t; size++; return true;
    }
    public Token dequeue(){
        if(isEmpty()) return null;
        Token t=arr[front]; front=(front+1)%capacity; size--; return t;
    }
    public Token peek(){ return isEmpty()?null:arr[front]; }
    public int getSize(){ return size; }
}

class MinHeap {
    private List<int[]> heap; // each entry: {patientId, severity}
    public MinHeap(){ heap=new ArrayList<>(); }
    public void insert(int pid,int severity){
        heap.add(new int[]{pid,severity});
        int i=heap.size()-1;
        while(i>0){
            int p=(i-1)/2;
            if(heap.get(p)[1] <= heap.get(i)[1]) break;
            Collections.swap(heap,p,i); i=p;
        }
    }
    public int[] extractMin(){
        if(heap.isEmpty()) return null;
        int[] min=heap.get(0);
        int[] last=heap.remove(heap.size()-1);
        if(!heap.isEmpty()){ heap.set(0,last); heapify(0); }
        return min;
    }
    private void heapify(int i){
        int n=heap.size(); int smallest=i;
        int l=2*i+1, r=2*i+2;
        if(l<n && heap.get(l)[1] < heap.get(smallest)[1]) smallest=l;
        if(r<n && heap.get(r)[1] < heap.get(smallest)[1]) smallest=r;
        if(smallest!=i){ Collections.swap(heap,i,smallest); heapify(smallest); }
    }
    public boolean isEmpty(){ return heap.isEmpty(); }
    public int size(){ return heap.size(); }
}

class HashTablePatients {
    private LinkedList<Patient>[] table;
    private int capacity = 101;
    public HashTablePatients(){
        table = new LinkedList[capacity];
        for(int i=0;i<capacity;i++) table[i]=new LinkedList<>();
    }
    private int hash(int key){ return Math.abs(key)%capacity; }
    public void upsert(Patient p){
        int idx=hash(p.id);
        for(Patient q: table[idx]) if(q.id==p.id){ q.name=p.name; q.age=p.age; q.severity=p.severity; return; }
        table[idx].add(p);
    }
    public Patient get(int id){
        int idx=hash(id);
        for(Patient p: table[idx]) if(p.id==id) return p;
        return null;
    }
    public boolean delete(int id){
        int idx=hash(id);
        Iterator<Patient> it=table[idx].iterator();
        while(it.hasNext()){
            if(it.next().id==id){ it.remove(); return true; }
        }
        return false;
    }
    public List<Patient> allPatients(){
        List<Patient> out=new ArrayList<>();
        for(LinkedList<Patient> bucket: table) out.addAll(bucket);
        return out;
    }
}

class UndoAction { String action; Token token; int patientId; int doctorId; int slotId;
    UndoAction(String action, Token token){ this.action=action; this.token=token; }
    UndoAction(String action, int pid, int did, int sid){ this.action=action; patientId=pid; doctorId=did; slotId=sid; }
}

class UndoStack {
    private Stack<UndoAction> st = new Stack<>();
    public void push(UndoAction a){ st.push(a); }
    public UndoAction pop(){ if(st.isEmpty()) return null; return st.pop(); }
    public boolean isEmpty(){ return st.isEmpty(); }
}

public class HospitalSystem {
    private HashTablePatients patients = new HashTablePatients();
    private Map<Integer,Doctor> doctors = new HashMap<>();
    private CircularQueue routineQueue = new CircularQueue(200);
    private MinHeap emergencyHeap = new MinHeap();
    private UndoStack undo = new UndoStack();
    private int nextTokenId = 1;

    public void registerPatient(int id, String name, int age, int severity){
        Patient p = new Patient(id,name,age,severity);
        patients.upsert(p);
        System.out.println("Patient registered: "+p);
    }

    public void addDoctor(int id, String name, String spec){
        doctors.put(id,new Doctor(id,name,spec));
        System.out.println("Added doctor: "+doctors.get(id));
    }

    public void addSlotToDoctor(int doctorId, int slotId, String start, String end){
        Doctor d = doctors.get(doctorId);
        if(d==null){ System.out.println("Doctor not found."); return; }
        d.addSlot(slotId,start,end);
        System.out.println("Slot added to doctor "+doctorId);
    }

    public void bookRoutine(int patientId, int doctorId){
        Patient p = patients.get(patientId);
        Doctor d = doctors.get(doctorId);
        if(p==null || d==null){ System.out.println("Patient or doctor missing."); return; }
        SlotNode free = d.findNextFree();
        if(free==null){ System.out.println("No free slot for doctor."); return; }
        free.booked=true;
        Token t = new Token(nextTokenId++, patientId, doctorId, free.slotId, "ROUTINE");
        boolean ok = routineQueue.enqueue(t);
        if(!ok){ System.out.println("Queue full. Booking failed."); free.booked=false; return; }
        undo.push(new UndoAction("book", t));
        System.out.println("Booked routine token: "+t);
    }

    public void emergencyIn(int patientId, int severity){
        Patient p = patients.get(patientId);
        if(p==null){ System.out.println("Patient not found."); return; }
        emergencyHeap.insert(patientId,severity);
        undo.push(new UndoAction("emergency", patientId, -1, -1));
        System.out.println("Emergency inserted: pid="+patientId+" severity="+severity);
    }

    public void serveNext(){
        if(!emergencyHeap.isEmpty()){
            int[] e = emergencyHeap.extractMin();
            int pid=e[0];
            Token t = new Token(nextTokenId++, pid, -1, -1, "EMERGENCY");
            undo.push(new UndoAction("serve", t));
            System.out.println("Served EMERGENCY patient: "+patients.get(pid));
            return;
        }
        Token t = routineQueue.dequeue();
        if(t==null){ System.out.println("No patients to serve."); return; }
        undo.push(new UndoAction("serve", t));
        Doctor d = doctors.get(t.doctorId);
        if(d!=null){
            SlotNode cur=d.head;
            while(cur!=null){ if(cur.slotId==t.slotId){ cur.booked=false; break; } cur=cur.next; }
        }
        System.out.println("Served ROUTINE token: "+t);
    }

    public void undoLast(){
        UndoAction a = undo.pop();
        if(a==null){ System.out.println("Nothing to undo."); return; }
        switch(a.action){
            case "book":
                Token tok = a.token;
                List<Token> temp = new ArrayList<>();
                while(!routineQueue.isEmpty()){ temp.add(routineQueue.dequeue()); }
                boolean removed=false;
                for(Token t: temp){
                    if(t.tokenId==tok.tokenId){ removed=true; continue; }
                    routineQueue.enqueue(t);
                }
                Doctor d = doctors.get(tok.doctorId);
                if(d!=null){ SlotNode cur=d.head; while(cur!=null){ if(cur.slotId==tok.slotId) {cur.booked=false; break;} cur=cur.next; } }
                System.out.println("Undo booking: removed token "+tok.tokenId + " removed? " + removed);
                break;
            case "emergency":
                System.out.println("Undo emergency: not fully reversible in this simplified implementation.");
                break;
            case "serve":
                System.out.println("Undo serve: manual verify required (not fully implemented).");
                break;
            default:
                System.out.println("Unknown undo action.");
        }
    }

    public void showReports(){
        System.out.println("=== Reports ===");
        for(Doctor d: doctors.values()){
            int pending=0; SlotNode cur=d.head;
            while(cur!=null){ if(cur.booked) pending++; cur=cur.next; }
            SlotNode nextFree=d.findNextFree();
            System.out.println(d + " pendingBookedSlots=" + pending + " nextFree=" + (nextFree==null?"none":nextFree));
        }
        System.out.println("Routine queue size: "+routineQueue.getSize());
        System.out.println("Emergency queue size: "+emergencyHeap.size());
        System.out.println("Total registered patients: "+patients.allPatients().size());
    }

    public static void main(String[] args) throws Exception {
        HospitalSystem hs = new HospitalSystem();
        Scanner sc = new Scanner(System.in);
        System.out.println("Hospital Triage System - CLI");
        hs.addDoctor(1,"Dr. Sharma","General");
        hs.addSlotToDoctor(1,101,"09:00","09:15");
        hs.addSlotToDoctor(1,102,"09:15","09:30");
        while(true){
            System.out.println("\nMenu:\n1.Register Patient\n2.Add Doctor Slot\n3.Book Routine Appointment\n4.Emergency Arrival\n5.Serve Next\n6.Undo Last\n7.Reports\n8.Exit\nChoose option:");
            int opt = -1;
            try{ opt = Integer.parseInt(sc.nextLine()); } catch(Exception e){ opt=-1; }
            if(opt==1){
                System.out.print("Patient id: "); int id=Integer.parseInt(sc.nextLine());
                System.out.print("Name: "); String name=sc.nextLine();
                System.out.print("Age: "); int age=Integer.parseInt(sc.nextLine());
                System.out.print("Severity (0-100): "); int sev=Integer.parseInt(sc.nextLine());
                hs.registerPatient(id,name,age,sev);
            } else if(opt==2){
                System.out.print("Doctor id: "); int did=Integer.parseInt(sc.nextLine());
                System.out.print("Slot id: "); int sid=Integer.parseInt(sc.nextLine());
                System.out.print("Start time: "); String st=sc.nextLine();
                System.out.print("End time: "); String et=sc.nextLine();
                hs.addSlotToDoctor(did,"unknown",""); // ensure doctor exists if not
                hs.addSlotToDoctor(did, sid, st, et);
            } else if(opt==3){
                System.out.print("Patient id: "); int pid=Integer.parseInt(sc.nextLine());
                System.out.print("Doctor id: "); int did=Integer.parseInt(sc.nextLine());
                hs.bookRoutine(pid,did);
            } else if(opt==4){
                System.out.print("Patient id: "); int pid=Integer.parseInt(sc.nextLine());
                System.out.print("Severity (lower = more critical): "); int sev=Integer.parseInt(sc.nextLine());
                hs.emergencyIn(pid,sev);
            } else if(opt==5){
                hs.serveNext();
            } else if(opt==6){
                hs.undoLast();
            } else if(opt==7){
                hs.showReports();
            } else if(opt==8){ System.out.println("Exit."); break; }
            else{ System.out.println("Invalid option."); }
        }
        sc.close();
    }
}
