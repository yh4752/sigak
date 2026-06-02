const REQUIRED_OPTIONS = ['labels', 'base-url', 'output-dir'];
const KNOWN_OPTIONS = new Set([...REQUIRED_OPTIONS, 'k', 'systems', 'limit']);
const KNOWN_SYSTEMS = new Set(['keyword', 'vector', 'hybrid', 'public']);

export function parseBenchmarkArgs(argv) {
  const options = parseOptions(argv);

  for (const optionName of REQUIRED_OPTIONS) {
    if (!options.has(optionName)) {
      throw new Error(`Missing required option: --${optionName}`);
    }
  }

  const k = options.has('k') ? parsePositiveInteger(options.get('k'), 'k') : 5;
  const limit = options.has('limit') ? parseBoundedInteger(options.get('limit'), 'limit', 1, 100) : 20;

  if (limit < k) {
    throw new Error('Option --limit must be greater than or equal to --k.');
  }

  return {
    labelsPath: options.get('labels'),
    baseUrl: options.get('base-url'),
    outputDir: options.get('output-dir'),
    k,
    limit,
    ...(options.has('systems') ? { systems: parseSystems(options.get('systems')) } : {}),
  };
}

function parseOptions(argv) {
  const options = new Map();

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];

    if (!arg.startsWith('--')) {
      throw new Error(`Invalid option format: ${arg}`);
    }

    const optionText = arg.slice(2);
    const equalsIndex = optionText.indexOf('=');
    const name = equalsIndex >= 0 ? optionText.slice(0, equalsIndex) : optionText;
    const rawValue = equalsIndex >= 0 ? optionText.slice(equalsIndex + 1) : argv[++index];

    if (!KNOWN_OPTIONS.has(name)) {
      throw new Error(`Unknown option: --${name}`);
    }

    if (rawValue === undefined || rawValue.startsWith('--')) {
      throw new Error(`Option --${name} requires a value.`);
    }

    const value = rawValue.trim();
    if (value.length === 0) {
      throw new Error(`Option --${name} must not be blank.`);
    }

    options.set(name, value);
  }

  return options;
}

function parsePositiveInteger(value, optionName) {
  if (!/^[1-9]\d*$/.test(value)) {
    throw new Error(`Option --${optionName} must be a positive integer.`);
  }

  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed)) {
    throw new Error(`Option --${optionName} must be a positive integer.`);
  }

  return parsed;
}

function parseBoundedInteger(value, optionName, min, max) {
  const parsed = parsePositiveInteger(value, optionName);

  if (parsed < min || parsed > max) {
    throw new Error(`Option --${optionName} must be an integer between ${min} and ${max}.`);
  }

  return parsed;
}

function parseSystems(value) {
  const rawSystems = value.split(',').map((system) => system.trim().toLowerCase());

  if (rawSystems.every((system) => system.length === 0)) {
    throw new Error('Option --systems must include at least one system.');
  }

  if (rawSystems.some((system) => system.length === 0)) {
    throw new Error('Option --systems must not include blank values.');
  }

  for (const system of rawSystems) {
    if (!KNOWN_SYSTEMS.has(system)) {
      throw new Error(`Option --systems contains unknown value: ${system}`);
    }
  }

  return [...new Set(rawSystems)];
}
