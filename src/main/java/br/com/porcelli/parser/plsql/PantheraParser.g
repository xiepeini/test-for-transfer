parser grammar PantheraParser;

options {
    output=AST;
    tokenVocab=PLSQLLexer;
}

import PLSQLParser;

tokens {
    CREATE_VIEW;
    DROP_VIEW;
}

@header {
package br.com.porcelli.parser.plsql;

}

@rulecatch {
catch (RecognitionException e) {
 reportError(e);
  throw e;
}
}

//OVERRIDE for explain statement
statement
options{
backtrack=true;
}
    :    create_view
    |    drop_view 
    |    alter_key swallow_to_semi  (SEMICOLON|EOF)
    |    grant_key swallow_to_semi  (SEMICOLON|EOF)
    |    truncate_key swallow_to_semi  (SEMICOLON|EOF)
    |    (begin_key) => body
    |    (declare_key) => block
    |    assignment_statement
    |    continue_statement
    |    exit_statement
    |    goto_statement
    |    if_statement
    |    loop_statement
    |    forall_statement
    |    null_statement
    |    raise_statement
    |    return_statement
    |    case_statement[true]
    |    sql_statement
    |    explain_statement 
    |    function_call
    ;

create_view
    :    create_key SQL92_RESERVED_VIEW tableview_name LEFT_PAREN column_name (COMMA column_name)* RIGHT_PAREN
        as_key select_statement
        -> ^(CREATE_VIEW tableview_name ^(COLUMNS column_name+) select_statement)
    ;

drop_view
    :    drop_key SQL92_RESERVED_VIEW tableview_name
        -> ^(DROP_VIEW tableview_name)
    ;

