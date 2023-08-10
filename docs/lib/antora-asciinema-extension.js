'use strict'

module.exports.register = (context, {config}) => {
	context.on('uiLoaded', appendAsciinemaScript(config))
	context.on('contentClassified', appendBlockProcessor)
}

const appendAsciinemaScript = (config) => ({uiCatalog}) => {

}

const appendBlockProcessor = ({siteAsciiDocConfig}) => {
	if (!siteAsciiDocConfig.extensions) siteAsciiDocConfig.extensions = []
	siteAsciiDocConfig.extensions.push({
			register: (registry, _context) => {
					registry.block("asciinema", processAsciinemaBlock)
					return registry
			}
	})
};

const processAsciinemaBlock = (context) => {
	context.onContext(['listing', 'literal'])
	context.positionalAttributes(['target', 'format'])
	context.process((parent, reader, attrs) => {
			const source = reader.getLines().join('\n');
			return toBlock(attrs, parent, source, context)
	})
};
module.exports.processAsciinemaBlock = processAsciinemaBlock

const toBlock = (attrs, parent, source, context) => {

	const block = context.$create_pass_block(
		parent,
		'hi',
		Opal.hash(attrs));

		return block
}

const fromHash = (hash) => {
	const object = {}
	// noinspection JSUnresolvedVariable
	const data = hash.$$smap
	for (const key in data) {
			object[key] = data[key]
	}
	return object
}
