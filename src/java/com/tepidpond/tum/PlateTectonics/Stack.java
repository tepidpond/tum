package com.tepidpond.tum.PlateTectonics;

import java.util.ArrayList;

public class Stack<T> extends ArrayList<T> {
	public void Push(T obj) {add(obj);}
	public T Pop() {return remove(size()-1);}
	public T Peek() {return get(size()-1);}
	public T Peek(int i) {return get(i);}
	public T Set(int i, T obj) {T temp = Peek(i); this.set(i, obj); return temp;}
	public boolean IsEmpty() {return size()==0;}
}
