package frontend;

import error.ErrorTable;
import frontend.ast.CompileUnit;
import frontend.symbol.SymbolTable;
import utils.Controller;

final public class Traverser {
    private final CompileUnit ast;
    private final ErrorTable errors = Controller.errors;
    private final SymbolTable symbols = Controller.symbols;
    
    public Traverser(CompileUnit ast) {
        this.ast = ast;
    }
    
    public void spawn() {
    
    }
}
