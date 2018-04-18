// Load asciidoctor.js and asciidoctor-reveal.js
var asciidoctor = require('asciidoctor.js')();
require('asciidoctor-reveal.js');

// Convert the document 'presentation.adoc' using the reveal.js converter
var attributes = {'revealjsdir': 'node_modules/reveal.js@'};
var options = {safe: 'unsafe', backend: 'revealjs', attributes: attributes, to_dir: '../../target/slides'};
asciidoctor.convertFile('index.adoc', options);
