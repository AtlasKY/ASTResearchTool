
import Handler.State
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration

class ConsistencyAnalysis {
	
	List handlers
	
	List results
	
	enum StateRes{
		NO_STATE,
		SAFE_READ,
		SAFE_WRITE,
		SAFE_RW,
		UNSAFE_R,
		UNSAFE_W,
		UNSAFE_RW
	}
	
	public ConsistencyAnalysis(List hdls) {
		
		handlers = new ArrayList()
		handlers = hdls
		
		results = new ArrayList<AnalysisResult>()
		
	}
	
	void analyse() {
		
		println "Analysis"
		
		for(int i = 0; i < handlers.size(); i++) {
			
			for(int j = i; j < handlers.size(); j++) {
				
				println "Handlers: " + handlers.get(i).name + " " + handlers.get(j).name
				
				//starts by calling the handler crossed with itself
				results.add(analysisHelper(handlers.get(i), handlers.get(j)))
				
			}	
		}
	}
	
	void print() {
		
		println ""
		
		results.each { res->
			println res
		}
	}
	
	AnalysisResult analysisHelper(Handler h1, Handler h2) {
		
		AnalysisResult ar = new AnalysisResult(h1, h2)
		
		ar = stateAnalysis(ar, h1, h2)
		
//		ar = userImpactAnalysis(ar, h1, h2)
		
//		ar = devModAnalysis(ar, h1, h2)
		
		return ar
	}
	
	AnalysisResult stateAnalysis(AnalysisResult ar, Handler h1, Handler h2) {
		
		StateRes s1 = StateRes.NO_STATE
		StateRes s2 = StateRes.NO_STATE
		
		if(h1.writeStates.size() > 0 && h1.readStates.size() > 0) {
			s1 = StateRes.SAFE_RW
		} else if(h1.writeStates.size() > 0) {
			s1 = StateRes.SAFE_WRITE
		} else if(h1.readStates.size() > 0) {
			s1 = StateRes.SAFE_READ
		} 
		
		if(h2.writeStates.size() > 0 && h2.readStates.size() > 0) {
			s2 = StateRes.SAFE_RW
		} else if(h2.writeStates.size() > 0) {
			s2 = StateRes.SAFE_WRITE
		} else if(h2.readStates.size() > 0) {
			s2 = StateRes.SAFE_READ
		} 

		//IF both handler are reading only then they are safe
		
		//if h1 writes state check if the states it writes to is read by the other handler
		//if so set both to unsafe
		// if not leave as is
		if((s1 == StateRes.SAFE_WRITE || s1 == StateRes.SAFE_RW)) {
			h1.writeStates.each { ws->
			//	println ws.path + " " + ws
				if(!stateReadHelper(h2 ,ws)) {
								
					//set the state results to unsafe
					if(s1 == StateRes.SAFE_WRITE)
						s1 = StateRes.UNSAFE_W
					else if(s1 == StateRes.SAFE_RW)
						s1 = StateRes.UNSAFE_RW
						
					if(s2 == StateRes.SAFE_READ) 
						s2 = StateRes.UNSAFE_R
					else if(s2 == StateRes.SAFE_RW)
						s2 = StateRes.UNSAFE_RW
				}
				
				if(s2 == StateRes.SAFE_WRITE || s2 == StateRes.SAFE_RW) {
					if(!stateWriteHelper(h2, ws)) {
						
						//println "state write helper passed false\n"
						//set the state results to unsafe
						if(s1 == StateRes.SAFE_WRITE)
							s1 = StateRes.UNSAFE_W
						else if(s1 == StateRes.SAFE_RW)
							s1 = StateRes.UNSAFE_RW

						if(s2 == StateRes.SAFE_WRITE)
							s2 = StateRes.UNSAFE_W
						else if(s2 == StateRes.SAFE_RW)
							s2 = StateRes.UNSAFE_RW
					}
				}
			}
		}
		
		//Repeat of the above check for 2nd handler
		if((s2 == StateRes.SAFE_WRITE || s2 == StateRes.SAFE_RW)) {
			h2.writeStates.each { ws->
				
				if(!stateReadHelper(h1 ,ws)) {
							
					//set the state results to unsafe
					if(s2 == StateRes.SAFE_WRITE)
						s2 = StateRes.UNSAFE_W
					else if(s2 == StateRes.SAFE_RW)
						s2 = StateRes.UNSAFE_RW
						
					if(s1 == StateRes.SAFE_READ) 
						s1 = StateRes.UNSAFE_R
					else if(s1 == StateRes.SAFE_RW)
						s1 = StateRes.UNSAFE_RW
				}
				if(s1 == StateRes.SAFE_WRITE || s1 == StateRes.SAFE_RW) {
					if(!stateWriteHelper(h2, ws)) {
					//	println "state write helper passed false\n"
						//set the state results to unsafe
						if(s2 == StateRes.SAFE_WRITE)
							s2 = StateRes.UNSAFE_W
						else if(s2 == StateRes.SAFE_RW)
							s2 = StateRes.UNSAFE_RW

						if(s1 == StateRes.SAFE_WRITE)
							s1 = StateRes.UNSAFE_W
						else if(s1 == StateRes.SAFE_RW)
							s1 = StateRes.UNSAFE_RW
					}
				}
			}
		}
		
		//println "S1: " + s1 + " S2: " + s2
		
		ar.stateRes(s1, s2)
		
		return ar
	}
	
	//checks for a given write state variable and the crossed handler's read states
	//return true if determined safe
	boolean stateReadHelper(Handler h, State st) {
		
		//if there are read states in the handler
		//cycle over each of them and check if modify the same state field
		
		boolean ret = true
		
		//no read state is safe for write
		if(h.readStates.size()>0) {
			
			h.readStates.each { rs->
			//	println "" + rs + " " + rs.path
				
				//does it modify the same state field?
				//if different fields are accessed then safe
				//if rs equals st then check
				if(rs.equals(st)) {
					
					//If it is not a safely scheduled write then check
					//if schedule is safe then safe
					if(stateSchSafe(st) == 2) {
						
						//does it have more than one unscheduled write to the same field
						if(writeCount(h, st) == 1) {
							
							//if it is a constant value that is set to the field
							//return safe
							if(!st.getPath().contains(":cont")) {
								ret = false//not safe with variable modifications
							}
						} else {
							ret = false //not safe with multiple unscheduled modifications
						}
					} else if(stateSchSafe(st) == 1) {
						//if unsafe scheduling
						ret = false
					}
				} 
			}
		} 
		
		return ret
	}
	
	boolean stateWriteHelper(Handler h, State st) {
		//if there are read states in the handler
		//cycle over each of them and check if modify the same state field
		
		boolean ret = true
			
		h.writeStates.each { ws->
		
			
			//does it modify the same state field?
			//if different fields are accessed then safe
			//if ws equals st then check
			if(ws.equals(st)) {
				
				//If it is not a safely scheduled write then check
				//if schedule is safe then safe
				if(stateSchSafe(st) != 0) {
					
					//does it have more than one unscheduled write to the same field
					if(writeCount(h, st) == 1) {
						
						//if it is a constant value that is set to the field
						//return safe
						if(!st.getPath().contains(":cont")) {
							ret = false//not safe with variable modifications
						}
					} else {
						ret = false //not safe with multiple unscheduled modifications
					}
				} 
			} 
		}
		
		return ret
	}
	
	//return the number of write states with the same state field access as s
	//returns 1 for a single unscheduled write of the same field
	int writeCount(Handler h, State s) {
		
		int i = 0
		
		h.writeStates.each { ws ->
			if(s.equals(ws))
				if(stateSchSafe(ws) == 2)
					i++
		}
//		println "Write Count: " + i + " " + s
		return i
	}
	
	//TODO: add a check for the values written to the state fields
	
	//TODO: add a helper for state time conditional checks
	
	//TODO: CREATE AN ENUM FOR THIS METHOD!!!!
	
	//return 0 if safe scheduler with overwrite
	//return 1 if unsafe no overwrite scheduling
	//return 2 if no scheduling
	int stateSchSafe(State s) {
		
		if(s.path.contains("so:")) {
			return 0 //safe overwrite scheduler
		} else if(s.path.contains("sf:")) {
			return 1 //unsafe no overwrite scheduler
		} else {
			return 2 //no scheduler
		}
		
	}
	
	void userImpactAnalysis(AnalysisResult ar, Handler h1, Handler h2) {
		
	}
	
	void devModAnalysis(AnalysisResult ar, Handler h1, Handler h2) {
		
	}
	
	//An object to store the result of the analysis
	//The handlers involved hdl1 and hdl2
	//The issues come across
	class AnalysisResult{
		
		Handler hdl1
		Handler hdl2
		
		int stateMod
		int usrImp
		int deviceMod
		
		String result
		
		public AnalysisResult(Handler h1, Handler h2) {
			
			hdl1 = h1
			hdl2 = h2
			result = ""
			
		}
		
		
		//TODO: Add more information to the prints/outcomes
		void stateRes(StateRes h1, StateRes h2) {
			
			//println "stateRes: " + h1 + " " + h2
			
			//0: no state usage
			//1: safe read state only
			//2: safe write state only
			//3: safe read and write
			//4: unsafe read state only
			//5: unsafe write state only
			//6: unsafe read and write
			if(h1 == StateRes.NO_STATE && h2 == StateRes.NO_STATE) {
				result += "STATE SAFE!\nNo state usage in the handlers " 
				result += "\n" + hdl1.name + " " + hdl2.name + "\n"
			}
			else if(h1 <= StateRes.SAFE_RW && h2 <= StateRes.SAFE_RW) {
				result += "STATE SAFE!\n"
				switch(h1) {
					case StateRes.NO_STATE:
						result += "Handler 1 does not use state\n"
						break;
					case StateRes.SAFE_READ:
						result += "Handler 1 " + hdl1.name + " reads state variables "
						result += readHelper(hdl1)
						break;
					case StateRes.SAFE_WRITE:
						result += "Handler 1 " + hdl1.name + " writes state variables "
						result += writeHelper(hdl1)
						break;
					case StateRes.SAFE_RW:
						result += "Handler 1 " + hdl1.name + " reads state variables "
						result += readHelper(hdl1)
						result += " writes state variables "
						result += writeHelper(hdl1)
						break;
				}
				result += "\n"
				switch(h2) {
					case StateRes.NO_STATE:
						result += "Handler 2 does not use state\n"
						break;
					case StateRes.SAFE_READ:
						result += "Handler 2 " + hdl2.name + " reads state variables "
						result += readHelper(hdl2)
						break;
					case StateRes.SAFE_WRITE:
						result += "Handler 2 " + hdl2.name + " writes state variables "
						result += writeHelper(hdl2)
						break;
					case StateRes.SAFE_RW:
						result += "Handler 2 " + hdl2.name + " reads state variables "
						result += readHelper(hdl2)
						result += " writes state variables "
						result += writeHelper(hdl2)
						break;
				}
				result += "\n"
			} else {
				result += "POSES STATE CONSISTENCY RISKS!\n"
				switch(h1) {
					case StateRes.UNSAFE_R:
						result += "Handler 1 " + hdl1.name + " reads state variables "
						result += readHelper(hdl1)
						break;
					case StateRes.UNSAFE_W:
						result += "Handler 1 " + hdl1.name + " writes state variables "
						result += writeHelper(hdl1)
						break;
					case StateRes.UNSAFE_RW:
						result += "Handler 1 " + hdl1.name + " reads state variables "
						result += readHelper(hdl1)
						result += " writes state variables "
						result += writeHelper(hdl1)
						break;
				}
				result += "\n"
				switch(h2) {
					case StateRes.UNSAFE_R:
						result += "Handler 2 " + hdl2.name + " reads state variables "
						result += readHelper(hdl2)
						break;
					case StateRes.UNSAFE_W:
						result += "Handler 2 " + hdl2.name + " writes state variables "
						result += writeHelper(hdl2)
						break;
					case StateRes.UNSAFE_RW:
						result += "Handler 2 " + hdl2.name + " reads state variables "
						result += readHelper(hdl2)
						result += " writes state variables "
						result += writeHelper(hdl2)
						break;
				}
				result += "\n"
			}
		}
		
		String readHelper(Handler h) {
			String str = ""
			
			h.readStates.each { s-> 
				if(s.path.contains("b:"))
					str += " boolean "
				
				if(s.path.contains("so:") || s.path.contains("sf:"))
					str += " schedule "
					
				str += "" + s + "; "
			}
			
			return str
		}
		
		String writeHelper(Handler h) {
			String str = ""
			
			h.writeStates.each { s->
				if(s.path.contains("b:"))
					str += " boolean "
				
				if(s.path.contains("so:") || s.path.contains("sf:"))
					str += " schedule "
					
				str += "" + s + "; "
			}
			
			return str
			
		}
		
		void usrImpRes(int res) {
			usrImp = res
		}
		
		void devModRes(int res) {
			deviceMod = res
		}
		
		@Override
		String toString() {
			return result
		}
		
	}
	
}
