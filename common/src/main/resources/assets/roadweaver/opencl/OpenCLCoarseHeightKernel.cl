#pragma OPENCL EXTENSION cl_khr_fp64 : enable

#define NODE_CONSTANT 0
#define NODE_ADD 1
#define NODE_MUL 2
#define NODE_MIN 3
#define NODE_MAX 4
#define NODE_CLAMP 5
#define NODE_ABS 6
#define NODE_SQUARE 7
#define NODE_CUBE 8
#define NODE_HALF_NEGATIVE 9
#define NODE_QUARTER_NEGATIVE 10
#define NODE_SQUEEZE 11
#define NODE_INVERT 12
#define NODE_Y_CLAMPED_GRADIENT 13
#define NODE_RANGE_CHOICE 14
#define NODE_NOISE 15
#define NODE_SHIFTED_NOISE 16
#define NODE_SHIFT_A 17
#define NODE_SHIFT_B 18
#define NODE_SHIFT 19
#define NODE_SPLINE 20
#define NODE_WEIRD_SCALED_SAMPLER 21
#define NODE_CLAMP_TO_NEAREST_UNIT 22
#define NODE_MARKER 23

#define PARAM_SAMPLE_COUNT 0
#define PARAM_MIN_Y 1
#define PARAM_MAX_Y 2
#define PARAM_CELL_HEIGHT 3
#define PARAM_MIN_X 4
#define PARAM_MIN_Z 5
#define PARAM_STEP 6
#define PARAM_SAMPLE_WIDTH 7
#define PARAM_ROOT_NODE 8
#define PARAM_NODE_COUNT 9

static int floor_int(double value) {
    int i = (int)value;
    return value < (double)i ? i - 1 : i;
}

static double lerp(double delta, double start, double end) {
    return start + delta * (end - start);
}

static double lerp2(double dx, double dy, double v00, double v10, double v01, double v11) {
    return lerp(dy, lerp(dx, v00, v10), lerp(dx, v01, v11));
}

static double lerp3(double dx, double dy, double dz,
                    double v000, double v100, double v010, double v110,
                    double v001, double v101, double v011, double v111) {
    return lerp(dz, lerp2(dx, dy, v000, v100, v010, v110), lerp2(dx, dy, v001, v101, v011, v111));
}

static double smoothstep(double value) {
    return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
}

static double clamp_double(double value, double min_value, double max_value) {
    return value < min_value ? min_value : fmin(value, max_value);
}

static double clamped_lerp(double start, double end, double delta) {
    if (delta < 0.0) {
        return start;
    }
    if (delta > 1.0) {
        return end;
    }
    return lerp(delta, start, end);
}

static double clamped_map(double value, double old_min, double old_max, double new_min, double new_max) {
    return clamped_lerp(new_min, new_max, (value - old_min) / (old_max - old_min));
}

static double perlin_wrap(double value) {
    return value - floor(value / 33554432.0 + 0.5) * 33554432.0;
}

static int permutation(__global const int* permutations, int noise_index, int value) {
    return permutations[noise_index * 256 + (value & 255)] & 255;
}

static double grad_dot(int gradient, double x, double y, double z) {
    switch (gradient & 15) {
        case 0: return x + y;
        case 1: return -x + y;
        case 2: return x - y;
        case 3: return -x - y;
        case 4: return x + z;
        case 5: return -x + z;
        case 6: return x - z;
        case 7: return -x - z;
        case 8: return y + z;
        case 9: return -y + z;
        case 10: return y - z;
        case 11: return -y - z;
        case 12: return x + y;
        case 13: return -y + z;
        case 14: return -x + y;
        default: return -y - z;
    }
}

static double improved_noise(__global const double* improved_values,
                             __global const int* permutations,
                             int noise_index,
                             double x,
                             double y,
                             double z,
                             double y_scale,
                             double y_max) {
    double shifted_x = x + improved_values[noise_index * 3];
    double shifted_y = y + improved_values[noise_index * 3 + 1];
    double shifted_z = z + improved_values[noise_index * 3 + 2];
    int floor_x = floor_int(shifted_x);
    int floor_y = floor_int(shifted_y);
    int floor_z = floor_int(shifted_z);
    double local_x = shifted_x - (double)floor_x;
    double local_y = shifted_y - (double)floor_y;
    double local_z = shifted_z - (double)floor_z;
    double y_offset = 0.0;
    if (y_scale != 0.0) {
        double y_limit = y_max >= 0.0 && y_max < local_y ? y_max : local_y;
        y_offset = floor(y_limit / y_scale + 0.0000001) * y_scale;
    }

    int i = permutation(permutations, noise_index, floor_x);
    int j = permutation(permutations, noise_index, floor_x + 1);
    int k = permutation(permutations, noise_index, i + floor_y);
    int l = permutation(permutations, noise_index, i + floor_y + 1);
    int i1 = permutation(permutations, noise_index, j + floor_y);
    int j1 = permutation(permutations, noise_index, j + floor_y + 1);
    double y_sample = local_y - y_offset;

    double d0 = grad_dot(permutation(permutations, noise_index, k + floor_z), local_x, y_sample, local_z);
    double d1 = grad_dot(permutation(permutations, noise_index, i1 + floor_z), local_x - 1.0, y_sample, local_z);
    double d2 = grad_dot(permutation(permutations, noise_index, l + floor_z), local_x, y_sample - 1.0, local_z);
    double d3 = grad_dot(permutation(permutations, noise_index, j1 + floor_z), local_x - 1.0, y_sample - 1.0, local_z);
    double d4 = grad_dot(permutation(permutations, noise_index, k + floor_z + 1), local_x, y_sample, local_z - 1.0);
    double d5 = grad_dot(permutation(permutations, noise_index, i1 + floor_z + 1), local_x - 1.0, y_sample, local_z - 1.0);
    double d6 = grad_dot(permutation(permutations, noise_index, l + floor_z + 1), local_x, y_sample - 1.0, local_z - 1.0);
    double d7 = grad_dot(permutation(permutations, noise_index, j1 + floor_z + 1), local_x - 1.0, y_sample - 1.0, local_z - 1.0);

    return lerp3(smoothstep(local_x), smoothstep(local_y), smoothstep(local_z), d0, d1, d2, d3, d4, d5, d6, d7);
}

static double perlin_noise(__global const int* perlin_ints,
                           __global const double* perlin_values,
                           __global const double* amplitudes,
                           __global const int* improved_indices,
                           __global const double* improved_values,
                           __global const int* permutations,
                           int noise_index,
                           double x,
                           double y,
                           double z) {
    if (noise_index < 0) {
        return 0.0;
    }
    int int_base = noise_index * 4;
    int octave_count = perlin_ints[int_base + 1];
    int amplitude_offset = perlin_ints[int_base + 2];
    int improved_offset = perlin_ints[int_base + 3];
    int value_base = noise_index * 3;
    double input_factor = perlin_values[value_base];
    double value_factor = perlin_values[value_base + 1];
    double result = 0.0;

    for (int i = 0; i < octave_count; i++) {
        int improved_index = improved_indices[improved_offset + i];
        if (improved_index >= 0) {
            double noise = improved_noise(
                    improved_values,
                    permutations,
                    improved_index,
                    perlin_wrap(x * input_factor),
                    perlin_wrap(y * input_factor),
                    perlin_wrap(z * input_factor),
                    0.0,
                    0.0);
            result += amplitudes[amplitude_offset + i] * noise * value_factor;
        }
        input_factor *= 2.0;
        value_factor /= 2.0;
    }
    return result;
}

static double normal_noise(__global const int* normal_ints,
                           __global const double* normal_values,
                           __global const int* perlin_ints,
                           __global const double* perlin_values,
                           __global const double* amplitudes,
                           __global const int* improved_indices,
                           __global const double* improved_values,
                           __global const int* permutations,
                           int noise_index,
                           double x,
                           double y,
                           double z) {
    if (noise_index < 0) {
        return 0.0;
    }
    int int_base = noise_index * 2;
    int first = normal_ints[int_base];
    int second = normal_ints[int_base + 1];
    double value_factor = normal_values[noise_index * 2];
    double input_factor = 1.0181268882175227;
    return (perlin_noise(perlin_ints, perlin_values, amplitudes, improved_indices, improved_values, permutations, first, x, y, z)
            + perlin_noise(perlin_ints, perlin_values, amplitudes, improved_indices, improved_values, permutations, second, x * input_factor, y * input_factor, z * input_factor)) * value_factor;
}

static double read_value(__global double* scratch, int base, int node) {
    return node < 0 ? 0.0 : scratch[base + node];
}

static double spaghetti_rarity_3d(double value) {
    if (value < -0.5) {
        return 0.75;
    }
    if (value < 0.0) {
        return 1.0;
    }
    return value < 0.5 ? 1.5 : 2.0;
}

static double spaghetti_rarity_2d(double value) {
    if (value < -0.75) {
        return 0.5;
    }
    if (value < -0.5) {
        return 0.75;
    }
    if (value < 0.5) {
        return 1.0;
    }
    return value < 0.75 ? 2.0 : 3.0;
}

static double weird_scaled_rarity(int mapper_type, double value) {
    return mapper_type == 2 ? spaghetti_rarity_2d(value) : spaghetti_rarity_3d(value);
}

static double clamp_to_nearest_unit(double value, int resolution) {
    int scaled = (int)(value * (double)resolution);
    float clamped = (float)(scaled + 1);
    return (double)(clamped / (float)resolution);
}

static double spline_value(__global const int* spline_ints,
                           __global const double* spline_locations,
                           __global const int* spline_value_nodes,
                           __global const double* spline_derivatives,
                           __global double* scratch,
                           int scratch_base,
                           int spline_index) {
    int int_base = spline_index * 3;
    int coordinate_node = spline_ints[int_base];
    int point_offset = spline_ints[int_base + 1];
    int point_count = spline_ints[int_base + 2];
    float coordinate = (float)read_value(scratch, scratch_base, coordinate_node);
    int last = point_count - 1;

    if (coordinate < (float)spline_locations[point_offset]) {
        float derivative = (float)spline_derivatives[point_offset];
        float value = (float)read_value(scratch, scratch_base, spline_value_nodes[point_offset]);
        float location = (float)spline_locations[point_offset];
        return (double)(derivative == 0.0f ? value : value + derivative * (coordinate - location));
    }

    if (coordinate >= (float)spline_locations[point_offset + last]) {
        float derivative = (float)spline_derivatives[point_offset + last];
        float value = (float)read_value(scratch, scratch_base, spline_value_nodes[point_offset + last]);
        float location = (float)spline_locations[point_offset + last];
        return (double)(derivative == 0.0f ? value : value + derivative * (coordinate - location));
    }

    int interval = 0;
    for (int i = 0; i < last; i++) {
        float next_location = (float)spline_locations[point_offset + i + 1];
        if (coordinate < next_location) {
            interval = i;
            break;
        }
    }

    float location0 = (float)spline_locations[point_offset + interval];
    float location1 = (float)spline_locations[point_offset + interval + 1];
    float delta = (coordinate - location0) / (location1 - location0);
    float value0 = (float)read_value(scratch, scratch_base, spline_value_nodes[point_offset + interval]);
    float value1 = (float)read_value(scratch, scratch_base, spline_value_nodes[point_offset + interval + 1]);
    float derivative0 = (float)spline_derivatives[point_offset + interval];
    float derivative1 = (float)spline_derivatives[point_offset + interval + 1];
    float distance = location1 - location0;
    float f8 = derivative0 * distance - (value1 - value0);
    float f9 = -derivative1 * distance + (value1 - value0);
    float result = value0 + delta * (value1 - value0) + delta * (1.0f - delta) * (f8 + delta * (f9 - f8));
    return (double)result;
}

static void evaluate_graph(__global const int* params,
                           __global const int* node_ints,
                           __global const double* node_values,
                           __global const int* normal_ints,
                           __global const double* normal_values,
                           __global const int* perlin_ints,
                           __global const double* perlin_values,
                           __global const double* amplitudes,
                           __global const int* improved_indices,
                           __global const double* improved_values,
                           __global const int* permutations,
                           __global const int* spline_ints,
                           __global const double* spline_locations,
                           __global const int* spline_value_nodes,
                           __global const double* spline_derivatives,
                           __global double* scratch,
                           int scratch_base,
                           int x,
                           int y,
                           int z) {
    int node_count = params[PARAM_NODE_COUNT];
    for (int node = 0; node < node_count; node++) {
        int int_base = node * 5;
        int type = node_ints[int_base];
        int left = node_ints[int_base + 1];
        int right = node_ints[int_base + 2];
        int extra_a = node_ints[int_base + 3];
        int extra_b = node_ints[int_base + 4];
        int value_base = node * 4;
        double value_a = node_values[value_base];
        double value_b = node_values[value_base + 1];
        double result = 0.0;
        double lhs = read_value(scratch, scratch_base, left);
        double rhs = read_value(scratch, scratch_base, right);

        switch (type) {
            case NODE_CONSTANT:
                result = value_a;
                break;
            case NODE_ADD:
                result = lhs + rhs;
                break;
            case NODE_MUL:
                result = lhs == 0.0 ? 0.0 : lhs * rhs;
                break;
            case NODE_MIN:
                result = fmin(lhs, rhs);
                break;
            case NODE_MAX:
                result = fmax(lhs, rhs);
                break;
            case NODE_CLAMP:
                result = clamp_double(lhs, value_a, value_b);
                break;
            case NODE_ABS:
                result = fabs(lhs);
                break;
            case NODE_SQUARE:
                result = lhs * lhs;
                break;
            case NODE_CUBE:
                result = lhs * lhs * lhs;
                break;
            case NODE_HALF_NEGATIVE:
                result = lhs > 0.0 ? lhs : lhs * 0.5;
                break;
            case NODE_QUARTER_NEGATIVE:
                result = lhs > 0.0 ? lhs : lhs * 0.25;
                break;
            case NODE_SQUEEZE: {
                double clamped = clamp_double(lhs, -1.0, 1.0);
                result = clamped / 2.0 - clamped * clamped * clamped / 24.0;
                break;
            }
            case NODE_INVERT:
                result = 1.0 / lhs;
                break;
            case NODE_Y_CLAMPED_GRADIENT:
                result = clamped_map((double)y, (double)extra_a, (double)extra_b, value_a, value_b);
                break;
            case NODE_RANGE_CHOICE:
                result = lhs >= value_a && lhs < value_b ? rhs : read_value(scratch, scratch_base, extra_a);
                break;
            case NODE_NOISE:
                result = normal_noise(normal_ints, normal_values, perlin_ints, perlin_values, amplitudes, improved_indices,
                        improved_values, permutations, extra_a, (double)x * value_a, (double)y * value_b, (double)z * value_a);
                break;
            case NODE_SHIFTED_NOISE:
                result = normal_noise(normal_ints, normal_values, perlin_ints, perlin_values, amplitudes, improved_indices,
                        improved_values, permutations, extra_b,
                        (double)x * value_a + lhs,
                        (double)y * value_b + rhs,
                        (double)z * value_a + read_value(scratch, scratch_base, extra_a));
                break;
            case NODE_SHIFT_A:
                result = normal_noise(normal_ints, normal_values, perlin_ints, perlin_values, amplitudes, improved_indices,
                        improved_values, permutations, extra_a, (double)x * 0.25, 0.0, (double)z * 0.25) * 4.0;
                break;
            case NODE_SHIFT_B:
                result = normal_noise(normal_ints, normal_values, perlin_ints, perlin_values, amplitudes, improved_indices,
                        improved_values, permutations, extra_a, (double)z * 0.25, (double)x * 0.25, 0.0) * 4.0;
                break;
            case NODE_SHIFT:
                result = normal_noise(normal_ints, normal_values, perlin_ints, perlin_values, amplitudes, improved_indices,
                        improved_values, permutations, extra_a, (double)x * 0.25, (double)y * 0.25, (double)z * 0.25) * 4.0;
                break;
            case NODE_SPLINE:
                result = spline_value(spline_ints, spline_locations, spline_value_nodes, spline_derivatives, scratch, scratch_base, extra_a);
                break;
            case NODE_WEIRD_SCALED_SAMPLER: {
                double rarity = weird_scaled_rarity(extra_b, lhs);
                result = rarity * fabs(normal_noise(normal_ints, normal_values, perlin_ints, perlin_values, amplitudes, improved_indices,
                        improved_values, permutations, extra_a, (double)x / rarity, (double)y / rarity, (double)z / rarity));
                break;
            }
            case NODE_CLAMP_TO_NEAREST_UNIT:
                result = clamp_to_nearest_unit(lhs, extra_a);
                break;
            default:
                result = 0.0;
                break;
        }
        scratch[scratch_base + node] = result;
    }
}

static int align_to_cpu_grid(int value) {
    int quotient = value / 4;
    if (value < 0 && value % 4 != 0) {
        quotient -= 1;
    }
    return quotient * 4;
}

__kernel void roadweaver_coarse_height_sample(
        __global const int* params,
        __global const int* node_ints,
        __global const double* node_values,
        __global const int* normal_ints,
        __global const double* normal_values,
        __global const int* perlin_ints,
        __global const double* perlin_values,
        __global const double* amplitudes,
        __global const int* improved_indices,
        __global const double* improved_values,
        __global const int* permutations,
        __global const int* spline_ints,
        __global const double* spline_locations,
        __global const int* spline_value_nodes,
        __global const double* spline_derivatives,
        __global double* scratch,
        __global int* heights
) {
    int index = get_global_id(0);
    int sample_count = params[PARAM_SAMPLE_COUNT];
    if (index >= sample_count) {
        return;
    }

    int sample_width = params[PARAM_SAMPLE_WIDTH];
    int sample_x = index % sample_width;
    int sample_z = index / sample_width;
    int x = align_to_cpu_grid(params[PARAM_MIN_X] + sample_x * params[PARAM_STEP]);
    int z = align_to_cpu_grid(params[PARAM_MIN_Z] + sample_z * params[PARAM_STEP]);
    int min_y = params[PARAM_MIN_Y];
    int max_y = params[PARAM_MAX_Y];
    int cell_height = params[PARAM_CELL_HEIGHT];
    int node_count = params[PARAM_NODE_COUNT];
    int root_node = params[PARAM_ROOT_NODE];
    int scratch_base = index * node_count;

    int result = min_y;
    for (int y = max_y; y >= min_y; y -= cell_height) {
        evaluate_graph(params, node_ints, node_values, normal_ints, normal_values, perlin_ints, perlin_values,
                amplitudes, improved_indices, improved_values, permutations, spline_ints, spline_locations,
                spline_value_nodes, spline_derivatives, scratch, scratch_base, x, y, z);
        double density = scratch[scratch_base + root_node];
        if (density > 0.390625) {
            result = y;
            break;
        }
    }
    heights[index] = result;
}