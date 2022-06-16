/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cli.runtime.command;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cli.SpringCliException;
import org.springframework.cli.runtime.engine.model.ModelPopulator;
import org.springframework.cli.util.IoUtils;
import org.springframework.shell.command.CommandContext;
import org.springframework.shell.command.CommandRegistration;
import org.springframework.shell.command.CommandRegistration.Builder;
import org.springframework.shell.command.CommandRegistration.OptionSpec;
import org.springframework.shell.command.CommandResolver;
import org.springframework.util.StringUtils;

/**
 * Implementation of a {@link CommandResolver} which discovers dynamic commands
 * from an existing structure under {@code .spring} directory.
 *
 * @author Janne Valkealahti
 */
public class DynamicCommandResolver implements CommandResolver {

	private static final Logger log = LoggerFactory.getLogger(DynamicCommandResolver.class);
	private Supplier<Path> pathProvider = () -> IoUtils.getWorkingDirectory().resolve(".spring").resolve("commands")
			.toAbsolutePath();
	private final List<ModelPopulator> modelPopulators;

	public DynamicCommandResolver(List<ModelPopulator> modelPopulators) {
		this.modelPopulators = modelPopulators;
	}

	@Override
	public List<CommandRegistration> resolve() {
		return buildRegistrations(scanCommands());
	}

	public void setPathProvider(Supplier<Path> pathProvider) {
		this.pathProvider = pathProvider;
	}

	protected CommandScanResults scanCommands() {
		Path path = this.pathProvider.get();
		log.debug("Looking for user-defined commands in directory " + path);
		CommandScanner scanner = new CommandScanner(path);
		CommandScanResults results = scanner.scan();
		log.debug("Found commands " + results);
		return results;
	}

	private List<CommandRegistration> buildRegistrations(CommandScanResults results) {
		return results.getCommandSubcommandMap().entrySet().stream()
			.flatMap(e -> e.getValue().stream().map(c -> CommandPair.of(e.getKey(), c)))
			.map(p -> {
				Builder builder = CommandRegistration.builder()
					.command(p.parent.getName(), p.child.getName())
					.description(p.child.getDescription())
					.group("User-defined Commands")
					.withTarget()
						.consumer(commandInvocation(p.parent.getName(), p.child.getName(), this.modelPopulators))
						.and();
				p.child.getOptions().stream().forEach(o -> {
					if (StringUtils.hasText(o.getName())) {
						addOption(o, builder);
					}
					else {
						log.warn("Option name not provided in subcommand " + p.child.getName());
					}
				});
				log.info("Adding command/subcommand " + p.parent.getName() + "/" + p.child.getName());
				return builder.build();
			})
			.collect(Collectors.toList());
	}

	private static Consumer<CommandContext> commandInvocation(String parentName, String childName,
			Iterable<ModelPopulator> modelPopulators) {
		return ctx -> {
			DynamicCommand dynamicCommand = new DynamicCommand(parentName, childName, modelPopulators);
			try {
				dynamicCommand.execute(ctx);
			} catch (Exception e) {
				throw new SpringCliException("error in dynamic command", e);
			}
		};
	}

	private static void addOption(CommandOption commandOption, Builder builder) {
		OptionSpec optionSpec = builder.withOption();
		if (StringUtils.hasText(commandOption.getName())) {
			optionSpec.longNames(commandOption.getName());
		}
		if (StringUtils.hasText(commandOption.getParamLabel())) {
			optionSpec.label(commandOption.getParamLabel());
		}
		if (StringUtils.hasText(commandOption.getDescription())) {
			optionSpec.description(commandOption.getDescription());
		}
		Optional<Class<?>> clazz = JavaTypeConverter.getJavaClass(commandOption.getDataType());
		if (clazz.isPresent()) {
			optionSpec.type(clazz.get());
		}
		if (StringUtils.hasText(commandOption.getDefaultValue())) {
			optionSpec.defaultValue(commandOption.getDefaultValue());
		}
		if (commandOption.isRequired()) {
			optionSpec.required(true);
		}
	}

	private static class CommandPair {
		Command parent;
		Command child;

		CommandPair(Command parent, Command child) {
			this.parent = parent;
			this.child = child;
		}

		static CommandPair of(Command parent, Command child) {
			return new CommandPair(parent, child);
		}
	}
}
