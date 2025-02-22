

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration


//The class that gets the files from directories, initialises the other classes, drives the tool
class CTAnalysisDriver {
	
	public static void main(String[] args) {
		 
		//CHANGE THIS TO YOU LOCAL DIRECTORY
		def proj_root = "C:/Users/AtlasKaan/Desktop/EclipseASTWorkspace/ASTAnalysis"
		
		def code_dir = proj_root + "/groovyApps"
		
		//iterate over the files in the directory								
		File dir = new File(code_dir).eachFile { file ->			
			
			//to check a single specific file out many, write the name in the contains check
			if(file.getName().toLowerCase().contains("energy")) {
				
				//create instance of CTAnalysisAST
				CTAnalysisAST ctal = new CTAnalysisAST()
				
				try {
					CompilerConfiguration cc = new CompilerConfiguration(CompilerConfiguration.DEFAULT)
					
					//add the compilation analysis object ctal to the compilation customiser
					cc.addCompilationCustomizers(ctal)
					
					//open a groovy shell
					GroovyShell gshell = new GroovyShell(cc)
					
					println "\n\n********************************************************************"
					println("\nAnalysing " + file.getName() + ":")
					
					//evaluate/run the file
					gshell.evaluate(file)
					
					println("Analysis Done!")
					
				}catch(MissingMethodException mme)
				{	
					def missingMethod = mme.toString()
					
					println(mme.getArguments())
					
					if(!missingMethod.contains("definition()"))
						println("missing method: " + missingMethod)
				}
				
				//Run the consistency analysis
				ConsistencyAnalysis conAn = new ConsistencyAnalysis(ctal.getHandlers())
				
				conAn.analyse()
				
				conAn.print()
			}
		}
		
		
	}
}
