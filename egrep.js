// Online JavaScript compiler (editor)
// Write and run JavaScript online using this JS editor.

function f(){
  const input = "a|b*"
  const output = ["|", "a", "*", "b"]
  console.log(output)
}

function regex_to_ast(input){
  
}

// * > . > |
// production de notation post fixe
// @FIXME : les parenthèse à traiter
function shunting_yard(input){
  let output = ""
  let stack = []
  
  for(const c of input){
    if(c === '*'){
      // on dépile juste les '*' vers la sortie qui viennent avant puis empile
      var obj = pop_inf_op(stack, "*")
      stack = obj.stack
      stack.push(c)
    }
    if(c === '.'){
      // dépile : '.', '*', puis empile
      var obj = pop_inf_op(stack, "*.")
      stack = obj.stack
      stack.push(c)
    }
    if(c === '|'){
      // on dépile tous les opérateurs, puis empile
      var obj = pop_inf_op(stack, "*.|")
      stack = obj.stack
      stack.push(c)
    }
    if(!"|.*".includes(c)){
      // ASCII
      output += c
    }else{
      output += obj.output
    }
  }
  // on dépile tout ce qui reste comme opérateurs
  return output + stack.reverse().join('')
}

function pop_inf_op(stack, ops){
  let output = ""
  let curr
  let end = false
  
  while(!end && stack.length){
    curr = stack.at(-1)
    // si c'est un opérateur
    if (ops.includes(curr)){
      stack.pop()
      output += curr
    }else{
      end  = true
    }
  }

  return {stack, output}
}

console.log(shunting_yard("a|b*.c"))
