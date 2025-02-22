import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement


//This class is an object that stores information for individual Event Handlers
//in a groovy SmartApp. Is a data structure and has no analysis within
//also has a subclass Method, that stores the information of a singular 
//method called from inside the Handler and the Handler stores 
//a list consisting of these Method objects
class Handler{
	
	boolean DEBUG = false
	
	String name //name of the handler
	String devName //the device associated with the handler
	
	boolean hasMsg //does it use messaging
	boolean isSch //does it use scheduling/is it a scheduled handler
	boolean schOverWrite //does it overwrite schedules
	boolean unSch //does it have unschedule methods
	
	List args //arguments to the handler
	List eventTriggers //events subscribed to
	List readStates //states read by the handler
	List writeStates //states written to by the handler
	List calledMethods //methods called by the handler
	List devMethods //device modification methods called by the handler
	List deviceAccesses //list of device accesses
	List eventProps //what info of the event is used, if used
	List timeAcc //list of time related info accesses
	List schMeths //methods that are scheduled for execution
	
	//Handler constructor method, initialise the flags, lists, and fields
	public Handler(String n, String dn, String en) {
		name = n
		devName = dn
		
		hasMsg = false
		isSch = false
		unSch = false
		schOverWrite = true
		
		eventTriggers = new ArrayList<String>()
		
		eventTriggers.add(en)
		
		args = new ArrayList<String>()
		timeAcc = new ArrayList()
		deviceAccesses = new ArrayList()
		calledMethods = new ArrayList<Method>()
		devMethods = new ArrayList<Method>()
		eventProps = new ArrayList()
		readStates = new ArrayList()
		writeStates = new ArrayList()
		schMeths = new ArrayList()
	}
	
	@Override
	boolean equals(Object o) {
		if(o instanceof Handler) {
			return this.name == o.name
		}
	}
	
	//update the scheduling information and flag
	void setSch(boolean owr) {
		isSch = true
		if(!owr) {
			schOverWrite = false
		}
	}
	
	//update usnchedule flag
	void setUnSch() {
		unSch = true
	}
	
	//update has message flag
	void setMsg(boolean b) {
		hasMsg = b
	}
	
	//Adds the arg string parameter of the handler call to the arguments list
	void addArg(String arg) {
		args.add(arg)
	}
	
	//Get an expression as string, finds the last index of the argument passed to the handler contained in the string
	//while there are still unprocessed presences of the handler parameter accesses,
	//walks through the string beginning at the last index of the parameter name and adds the chars to a string
	//until it reads a space, =, !, ?, or a closing parenthesis without an opening parenthesis immediately before it
	//adds the constructed string to the list of event properties accessed arraylist if it's not already present
	void addEvtProp(String prop) {
		if(!eventProps.contains(prop)) {
			def evt = ""
			
			def k = prop.length()
			
			args.each { arg->
			
				while((k = prop.lastIndexOf(arg, k-1))!= -1) {
					def i = k
					evt = ""
					while(i < prop.size() && (prop.getAt(i)!= " " &&
						((prop.getAt(i-1) != "(")? prop.getAt(i)!= ")":true) && prop.getAt(i)!= "=" && prop.getAt(i)!= "!"
						&& prop.getAt(i)!= "?")) {
					
						evt = evt + prop.getAt(i)
				//		println "Event Name: " + i +" "+ k + evt
						i++
					}
					if(!eventProps.contains(evt))
						eventProps.add(evt)
				}
				
			}
		}
	}
	
	//add event
	void addEvent(String evt) {
		eventTriggers.add(evt)
	}
	
	//basic method for add state
	void addReadState(String s, String pth) {
		addReadState(s, pth, null)
	}
	
	//overloaded method to add read state
	void addReadState(String s, String pth, Expression exp) {
		
		//create a new state object
		State st = new State(s, pth)
		
		//if the exp is not null, add the exp as the boolean read to the state
		if(exp != null)
			st.addBoolRead(exp)
		
		//if there are read states in the handler, check if the current state is present
		//if not then add it
		if(readStates.size()>0) {
			boolean hasSt = false
			//if has the same path state object then not add
			//but if it doesn't have it then add
			readStates.each { rs->
				if(!hasSt)
					hasSt = rs.equals(st, true)
			}
			
			if(!hasSt) {
				readStates.add(st)
			}
		} else {
			readStates.add(st)
		}
	}
	
	//method to add a state the handler writes to
	void addWriteState(String s, String pth, Expression exp) {
		State st = new State(s, pth)
		
		//store the expression in the state object
		st.setWrite(exp)
		
		//if the state exists in the write states list, then dont add
		//else add the state
		if(writeStates.size()>0) {
			boolean hasSt = false
			//if has the same path state object then not add
			//but if it doesn't have it then add
			writeStates.each { rs->
				if(!hasSt)
					hasSt = rs.equals(st, true)
			}
			
			if(!hasSt) {
				writeStates.add(st)
			}
		} else {
			writeStates.add(st)
		}
	}
	
	//add a non-duplicate device access
	void addDevAcc(String s) {
		if(!deviceAccesses.contains(s))
			deviceAccesses.add(s)
	}
	
	//helper for checking whether a method is called on a device
	boolean devMethHelper(String rec, List devices) {
		
		def ret = false
		
		if(rec.contains("log") || rec.contains("this"))
			return false
							
		devices.each { d->
			if(d.devName.contains(rec)) {
				ret = true
			}
		}
		return ret
	}
	
	//default method to add a method call to the list
	void addMethodCall(MethodCallExpression mexp, String path, boolean sta, boolean dev) {
		addMethodCall(mexp, mexp.getReceiver().getText(), path, sta, dev)
	}
	
	//overloaded method to add a method call to the list
	void addMethodCall(MethodCallExpression mexp, String receiver,  String path, boolean sta, boolean dev) {
		
		//name of the method
		String mName = receiver + "." + mexp.getMethodAsString()
		
		def rec = receiver
		
		//check for a device call tag in the path
		if(path.contains("d:")) {
			def devin = path.indexOf("d:")+2
			def devname = ""
			while(devin < path.length() && path.getAt(devin)!= ":") {
				devname += path.getAt(devin)
				devin++
			}
			rec = devname
		}
		//create a method object
		Method m = new Method(rec, mexp.getMethodAsString())
		
		//if method has schedule
		if(path.contains("so:")) {
			m.setSch()
		}
		
		m.setCallPath(path)
		
		//if it uses state information in the method
		if(sta) {
			m.setState()
			String state = ""
			boolean isWrite = false
			def ins = path.size() 
			
			//gets the state information from the path and adds it to method object
			ins = path.lastIndexOf("st:", ins)
			while( ins != -1) {
				def i = ins + 3
				while(i < path.size() && path.getAt(i) != ":") {
					state += path.getAt(i)
					i++
				}
				ins = path.lastIndexOf("st:", ins-1)
			}

			//if the state is contained in the write state list, then it is a state write
			if(writeStates.contains(state)) {
				isWrite = true
			}
			m.addState(state, isWrite)
		}

		//if the method is under a conditional block		
		if(path.contains("c:")) {
		//	println "\nHandler: " + name
			if(!m.isCond)
				m.setCond()
		}
		
		//.each{} call handling for method registration
		if(mexp.getText().contains("each")) {
	//		println "For each loop Meth Expression:\n" + mexp
			mexp.getArguments().each { arg->
				if(arg instanceof ClosureExpression) {
					if(arg.getCode() instanceof BlockStatement) {
						BlockStatement bl = arg.getCode()
		//				println "Block statements: " + bl.getStatements()
						bl.getStatements().each { st->
			//				println "Statement " + st
							if(st instanceof ExpressionStatement) {
								Expression xp = st.getExpression()
				//				println "Expression: " + xp
				//				println "Method arguments: " + m.arguments
								if(xp instanceof MethodCallExpression) {
									m.addArg(xp)
									addMethodCall(xp, rec, path, sta, dev)
								}
							}
						}
					}
				}
			}
		}	//if the method is a logging method, get the arguments
		else if(!mName.contains("log")){
			m.addArg(mexp.getArguments())
		}
		
		//if the method already exists in the list, dont add
		if(!calledMethods.contains(m)) {
			calledMethods.add(m)
		}
		
		//check to add device modification methods
		//do not add device.each-like iterating methods
		if(dev && !devMethods.contains(m)) {
			if(mName.contains("size"))
				return
			
			if(mexp.getArguments().size()>0 
				&& !(mexp.getArguments().getAt(0) instanceof ClosureExpression)) {
				devMethods.add(m)
			}
			else if(mexp.getArguments().size() == 0) {
				devMethods.add(m)
			}
		}
		
		//If the method is a schedule method
		if(m.method.contains("schedule") || m.method.toLowerCase().contains("runin") || m.method.toLowerCase().contains("runevery")) {
			if(DEBUG) println "Schedule meth: " + m
			schMeths.add(m)
		}
		
	}
	
	//add time access to the handler
	void addTimAcc(String s) {
		if(!timeAcc.contains(s))
			timeAcc.add(s)
	}
	
	//returns the index of the method with the name m
	int getMeth(String m) {
		def i = 0
		while(!calledMethods.get(i).getM().contains(m)) {
			i++
		}
		return i
	}
	
	//handler string output
	@Override
	public String toString() {
		def state = ""
		def methods = ""
		def devMeth = ""
		def schMeth = ""
		def stMeth = ""
		def triggers = ""
		def evprops = ""
		def tAcc = ""
		def msg = ""
		def sch = ""
		def nm = "Handler Name: " + name
		nm = nm + "("
		if(args.size()>0) {
			args.each { arg->
				nm = nm + arg
			}
		}
		nm = nm + ")"
		if(isSch) {
			sch = "\nSchedule Overwrite: " + schOverWrite
		}
		if(eventProps.size()>0) {
			evprops = "\nEvent Info Used: "
			eventProps.each { p->
				evprops = evprops + p + "; "
			}
		}
		if(calledMethods.size()>0) {
			methods = "\nCalled Methods: "
			calledMethods.each { m->
				if(!m.method.contains("log")) {
					methods = methods + m + "; "
				}
				if(m.useState) {
					stMeth += "	" + m.extString() + "\n"
				}
			}
		}
		if(devMethods.size()>0) {
			devMeth = "\nDevice Methods: \n"
			devMethods.each { m->
				devMeth += "	" + m.extString() + "\n"
			}
		}
		if(schMeths.size()>0) {
			schMeth = "\nSchedule Methods: \n"
			schMeths.each { m->
				schMeth += "	SchM: " + m + "\n"
			}
		}
		if(readStates.size()>0) {
			state = "\nRead States: "
			readStates.each { st->
				state = state + st + "; "
			}
		}
		if(writeStates.size()>0) {
			state = state + "\nWrite States: "
			writeStates.each { st->
				state = state + st + "; "
			}
		}
		if(eventTriggers.size()>0) {
			triggers = "\nEvent Triggers: "
			eventTriggers.each { tr->
				triggers = triggers + tr + "; "
			}
		}
		if(timeAcc.size()>0) {
			tAcc = "\nTime Access: "
			timeAcc.each { ta->
				tAcc += ta + ";"
			}
		}
		if(hasMsg) {
			msg = "\nSend Notification/Msg"
		}
		
		return nm + "\nDevice Name: " + devName + triggers + sch + evprops + methods + state + 
		 msg + tAcc + devMeth + schMeth + stMeth + "\n"
	}
	
	
	
	class Method{
		
		String receiver //receiver of the method call
		String method //name of the method
		String callPath //path to the method call
		List arguments //arguments passed to the method call
		boolean isCond //is it under conditional block
		boolean useState //does it use state fields, read/write
		boolean isSch //is it scheduled
		String condPar 
		List rState //read states
		List wState //written states
		List evtVal //event value accesses
		
		//method constructor
		public Method(String r) {
			this.Method(r, "")
		}
		
		//overloaded constructor method
		//initialise the values and objects
		public Method(String r, String m) {
			receiver = r
			arguments = new ArrayList()
			method = m
			isCond = false
			useState = false
			isSch = false
			condPar = ""
			evtVal = new ArrayList()
			rState = new ArrayList()
			wState = new ArrayList()
		}
		
		//set for schedule flag
		void setSch() {
			isSch = true
		}
		
		//set for state flag
		void setState() {
			useState = true
		}
		
		//add a new state to the list
		void addState(String s, boolean isWrite) {
			if(isWrite) {
				wState.add(s)
			} else {
				rState.add(s)
			}
		}
		
		//getter for method name
		String getM() {
			return method
		}
		
		//getter for receiver name
		String getRec() {
			return receiver
		}
		
		//setter for path
		void setPath(String pth) {
			callPath = pth
		}
		
		//setter for method name
		void addMethod(String m) {
			method = m
		}
		
		//adds a new argument to the list of arguments as an expression
		void addArg(Expression argexp) {
			if(argexp instanceof ArgumentListExpression) {
				argexp.each { xp->
					arguments.add(xp)
				}
			}else {
				arguments.add(argexp)
			}
		}
		
		//sets the conditional info
		//updates the condPar variable
		void setCond() {
			
			isCond = true
			def ind = 3
			String p = ""
			while(ind <= callPath.length()) {
				p = callPath.substring(ind-3, ind)
				if(p.contains("t-")) {
					condPar += "time-info:"
					ind+=2
				}
				else if(p.contains("s-")) {
					condPar += "state-info:"
					ind+=2
				}
				else if(p.contains("e-")) {
					condPar += "evt-info("
					p = callPath.substring(ind-3)
					def i = (p.indexOf("e-") + 2)
					def evt = ""
					while(i < p.length() && p.getAt(i)!="|") {
						evt += p.getAt(i)
						i++
					}
					evtVal.add(evt)
					condPar += evt + "):"
					ind += i
				}
				else if(p.contains("i-")) {
					condPar += "no-else:"
					ind+=2
				}
				else if(p.contains("ic:")) {
					condPar += "if:"
					ind+=3
				}
				else if(p.contains("ec:")) {
					condPar += "else:"
					ind+=3
				}
				else
					ind++
				
			}
		}
		
		//equals override for better comparison
		@Override
		boolean equals(Object o) {
			if(o instanceof Method) {
				return (this.receiver.equals(o.receiver) && this.method.equals(o.method) && (this.condPar == o.condPar))
			}
			else if(o instanceof String) {
				return this.method.contains(o)
			}
			else
				false
		}
		
		//called for an extensive output rather than the normal string output
		String extString() {
			def str = this.toString() + "\n"
			if(isCond)
				str += "		Conditionals: " + condPar + "\n"
			
			if(useState) {
				str += "		State: "
				rState.each { s->
					str += "R: " + s + "; "
				}
				wState.each { s->
					str += "W: " + s + "; "
				}
				str += "\n"
			}
			
			if(evtVal.size()>0)
			{
				str += "		Event Values:"
				evtVal.each { e->
					str += " " + e
				}
				str += "\n"
			}
			
			return str
		}
		
		//stringify the method
		@Override
		public String toString() {
			String st = receiver + "." + method + "("
			arguments.each { exp->
				st = st + exp.getText()
				if(arguments.size()-arguments.indexOf(exp)-1 > 0) {
					st = st + ", "
				}
			}
			st = st + ")"
			return st
		}
		
	}
	
	class State{
		
		String state //state name
		String path //path of the state
		
		String writeVal //value written to the state
		Expression writeExp //expression of the write value
		boolean isWrite //is this a write state
		
		String boolVal //boolean value
		Expression readExp //expression of the read
		boolean boolRead //is it a boolean read
		
		//state constructor method
		//initialise fields
		public State(String s, String p) {
			 state = s
			 path = p
			 
			 isWrite = false
			 writeVal = ""
			 writeExp = null
			 
			 boolRead = false
			 boolVal = ""
			 readExp = null
		}
		
		//set this state as a write state with exp as the write value
		void setWrite(Expression exp) {
			isWrite = true
			writeVal = exp.getText()
			writeExp = exp
		}
		
		//set this as a boolean read state
		void addBoolRead(Expression exp) {
			boolRead = true
			readExp = exp
			boolVal = exp.getText()
		}
		
		@Override
		boolean equals(Object o) {
			if(o instanceof State) {
				return this.state.equals(o.state)
			} else
				return false
		}
		
		//overloaded equals check
		boolean equals(Object o, boolean checkPath) {
			if(checkPath) {
				return this.state.equals(o.state) && this.path.equals(o.path)
			}else {
				return this.equals(o)
			}
		}
		
		@Override
		String toString() {
			return state
		}
	}
}