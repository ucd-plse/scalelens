import numpy as np
import similaritymeasures
import matplotlib.pyplot as plt
import math
from os import listdir
from os.path import isfile, join
import os
import shutil
import sys
import random
from scipy import stats
from scipy.stats import linregress
import json
from json import JSONEncoder


THIS_FILE = os.path.basename(__file__)
ENABLE_DEBUG = False
# these two variables should be maintained as false until we clean up the code
NORMALIZE_FOR_COMPARISON = False
NORMALIZE_FOR_PLOTTING = False
CSV_SEPARATOR = ','
LOG_BASE = 2
MAX_DECIMALS = 12
DISTANCE_NAMES = ['pcm', 'frechet_dist',
                  'area_between_two_curves', 'curve_length_measure', 'dtw']
N_DIM_DISTANCES = ['frechet_dist', 'dtw']
EXPERIMENT_LW = 4
EXPERIMENT_LBL = 'Exp'
EXPERIMENT_COLOR = 'b'
FIT_COLORS = ['r--']
FIT_LW = '2'
SUPER_LINEAR_CATEGORY_NAME = 'super-linear'
BURSTY_LINEAR_CATEGORY_NAME = 'bursty'
BURSTY_LINEAR_COMP_NAME = 'Burst(x, y)'
BURSTY_LINEAR_COMP_NAME_SINGLE = 'Burst(x)'
CONSTANT_LINEAR_CATEGORY_NAME = 'large-linear'
LINEAR_CATEGORY_NAME = 'linear'
SUPRA_CATEGORY_NAME = 'supra-linear'
BURSTY_LINEAR_FUNCTION_NAME = 'B'
NO_DATA_MIN_POINTS = 2
BURSTY_PERCENTAGE = 0.1
SLOW_GROWTH = 0.1
SMALL_DATA_POINT_SIZE = 2
SIGNIFICANT_MULTIPLIER_DIGITS = 3
SUMMARY_NAME = 'distance_summary.csv'
SD_MERGED_NAME = 'sdeps.json'
JSON_NAME = 'elements.json'
SUMMARY_COLUMNS = 'Method,Line Number,Category,Complexity'
LOOP_TYPE = 'Loop'
CROSS_TYPE = 'Cross'
DO_MERGE = 'merge'
DO_DISTANCES = 'complexity'
DO_CLEAN = 'clean'


def LOG_LINE(line, *args):
    print("[{:s}] ".format(THIS_FILE) + line.format(*args))


def DEBUG_LINE(line, *args):
    if ENABLE_DEBUG == True:
        print("[{:s}] ".format(THIS_FILE) + line.format(*args))


'''
These are the functions for which we compare against. Its a large set that can be 
enlarged if needed.
'''


class TargetFunctions:

    functions = {}
    twoDimFunctions = {}
    measures = {}
    categories = {}

    def __init__(self):
        # the target functions for a single dimension. Most of these are
        # simple models and are used to see a trend in the data
        self.functions['x'] = lambda x: x
        self.functions['x^2'] = lambda x: x * x
        self.functions['x^3'] = lambda x: x * x * x
        self.functions['log(x)'] = lambda x: math.log(x, LOG_BASE)
        self.functions['x * log(x)'] = lambda x: x * math.log(x, LOG_BASE)
        # two dimensional functions in case we need them
        self.twoDimFunctions['x'] = lambda x, y: x
        self.twoDimFunctions['y'] = lambda x, y: y
        self.twoDimFunctions['log(x)'] = lambda x, y: math.log(x, LOG_BASE)
        self.twoDimFunctions['log(y)'] = lambda x, y: math.log(y, LOG_BASE)
        self.twoDimFunctions['log(x * y)'] = lambda x, y: math.log(x * y, LOG_BASE)
        self.twoDimFunctions['x * y'] = lambda x, y: x * y
        self.twoDimFunctions['x^2'] = lambda x, y: x * x
        self.twoDimFunctions['x^3'] = lambda x, y: x * x * x
        self.twoDimFunctions['y^2'] = lambda x, y: y * y
        self.twoDimFunctions['y^3'] = lambda x, y: y * y * y
        self.twoDimFunctions['x^2 * y'] = lambda x, y: x * x * y
        self.twoDimFunctions['x * y^2'] = lambda x, y: x * y * y
        self.twoDimFunctions['x^2 * y^2'] = lambda x, y: x * x * y * y
        self.twoDimFunctions['x * y * log(x * y)'] = lambda x, y: x * \
            y * math.log(x * y, LOG_BASE)
        # the distance measures
        self.measures[DISTANCE_NAMES[0]
                      ] = lambda x, y: similaritymeasures.pcm(x, y)
        self.measures[DISTANCE_NAMES[1]
                      ] = lambda x, y: similaritymeasures.frechet_dist(x, y)
        self.measures[DISTANCE_NAMES[2]
                      ] = lambda x, y: similaritymeasures.area_between_two_curves(x, y)
        self.measures[DISTANCE_NAMES[3]
                      ] = lambda x, y: similaritymeasures.curve_length_measure(x, y)
        self.measures[DISTANCE_NAMES[4]] = lambda x, y: self.__doDtw(x, y)
        # the function categories
        self.categories[SUPRA_CATEGORY_NAME] = [
            'log(x)', 'log(y)', 'log(x * y)']
        self.categories[LINEAR_CATEGORY_NAME] = ['x', 'y']
        self.categories['quasi-linear'] = ['x * log(x)', 'x * y * log(x * y)']
        self.categories[SUPER_LINEAR_CATEGORY_NAME] = [
            'x^2', 'x^3', 'y^2', 'y^3', 'x * y', 'x^2 * y', 'x * y^2', 'x^2 * y^2']
        self.categories[BURSTY_LINEAR_CATEGORY_NAME] = [
            BURSTY_LINEAR_FUNCTION_NAME]
        # 3 this one is empty since its decided after
        self.categories[CONSTANT_LINEAR_CATEGORY_NAME] = None

    '''
  Get the category for centain function.
  '''

    def mapFunctionCategory(self, functionName):
        for name in self.categories.keys():
            if functionName in self.categories[name]:
                return name
        return None

    '''
  Utility to know if a function name is super linear or not
  '''

    def isLinearCategory(self, name):
        return name in self.categories[LINEAR_CATEGORY_NAME]

    '''
  This distance measure has a different return value, so it has to be handled differently.
  '''

    def __doDtw(self, x, y):
        (dtw, _) = similaritymeasures.dtw(x, y)
        return dtw


'''
A set of useful functions to use accross the classification.
'''


class SimilarityFit:
    '''
    Normalizes a given set of data based on the mean, maximum and minimum of 
    the set.
    '''

    def normalizeAxis(self, axis):
        n = []
        mi = min(axis)
        ma = max(axis)
        mean = 0
        for i in axis:
            mean = mean + i
        mean = mean / len(axis)
        for i in axis:
            n.append(((i - mean) / (ma - mi if ma != mi else 1)))
        return n

    '''
  Given a x axis, it generates data based on it for a given function. For example, 
  f(x) = log(x) and x = [1, 2] returns [log(1), log(2)]. It also conditionally 
  normalizes the output. It also works for more than one dimension (two in this case).
  '''

    def generateDataForFunction(self, datapoints, targetFunction, normalizeOutput):
        y1 = []
        numDims = len(datapoints[0]) - 1
        if numDims == 1:
            for i in datapoints[:, 0]:
                value = targetFunction(i) if i > 0 else 0
                y1.append(value)
        elif numDims == 2:
            for i, j in zip(datapoints[:, 0], datapoints[:, 1]):
                value = targetFunction(i, j) if i*j > 0 else 0
                y1.append(value)
        else:
            LOG_LINE(
                '[ERROR] Unexpected number of dimensions [{:d}]', numDims + 1)
            sys.exit(1)
        if normalizeOutput == True:
            y1 = self.normalizeAxis(y1)
        data = np.zeros((len(datapoints[:, 0]), numDims + 1))
        data[:, 0] = datapoints[:, 0]
        if numDims == 1:
            data[:, 1] = y1
        elif numDims == 2:
            data[:, 1] = datapoints[:, 1]
            data[:, 2] = y1
        return data
    '''
  Calculates distance using some of the selected measure
  '''

    def calculateDistanceFor(self, distanceMeasure, experimentalData, functionData):
        return distanceMeasure(experimentalData, functionData)

    '''
  Chooses the best fit considering a set of target functions. It returns the smaller fit 
  (closest to 0) for the selected measure
  '''

    def chooseBestFit(self, functions, experimentalData, normalizeOutput, distanceMeasure, elementName, rawData):
        allFits = {}
        allScores = []
        # 3 first we will normalize the data for weighting
        numDims = len(experimentalData[0]) - 1
        normalizer = SimilarityFit()
        normalizedData = np.zeros((len(experimentalData[:, 0]), numDims + 1))
        normalizedData[:, 0] = experimentalData[:, 0]
        if numDims == 1:
            normalizedData[:, 1] = normalizer.normalizeAxis(rawData)
        elif numDims == 2:
            normalizedData[:, 1] = experimentalData[:, 1]
            normalizedData[:, 2] = normalizer.normalizeAxis(rawData)
        # do for each target function
        for name, lambdaFunction in functions.items():
            functionData = self.generateDataForFunction(
                experimentalData, lambdaFunction, False)
            normalizedFunctionData = self.generateDataForFunction(
                experimentalData, lambdaFunction, True)
            # here we have the data generated, now we will create two metrics: one normalized and one not
            # normalized. We will weight to get the final output.
            calculatedDistance = round(self.calculateDistanceFor(
                distanceMeasure, experimentalData, functionData), MAX_DECIMALS)
            normalizedDistance = round(self.calculateDistanceFor(
                distanceMeasure, normalizedData, normalizedFunctionData), MAX_DECIMALS)
            score = calculatedDistance * normalizedDistance
            # if elementName.find('.119') != -1:
            # LOG_LINE('[{:s}], d1=[{:f}], d2=[{:f}]', name, calculatedDistance, normalizedDistance)
            allScores.append(score)
            if score not in allFits:
                allFits[score] = [{name: normalizedFunctionData}]
            else:
                allFits[score].append({name: normalizedFunctionData})
        # sort keys by calculated distance
        sortedKeys = sorted(allFits.keys())
        # and make sure the experimental data gets normalized
        experimentalData[:, numDims] = normalizedData[:, numDims]
        return (sortedKeys, allFits, allScores)


'''
Represents an element in the input directory.
'''


class SDElement:
    name = None
    dataFile = None
    plotFile = None
    rawDataFile = None
    experimentalData = None
    bestFits = None
    bestFitValue = None
    couldbeSuperLinear = None
    avgLinearFactor = None
    factoredData = None
    newFunctionLabel = None

    def __init__(self, name):
        self.name = name
        self.dataFile = "{:s}.dat".format(self.name)
        self.rawDataFile = "{:s}.raw".format(self.name)
        self.plotFile = "{:s}.png".format(self.name)
        self.couldbeSuperLinear = False
        self.avgLinearFactor = 0


'''
Represents an element in the output directory to be written as json.
'''


class JSONOutputElement:
    name = None
    lineNumber = None
    dependencyType = None
    dimensionalDescriptors = None

    def __init__(self, name, lineNumber, dependencyType):
        self.name = name
        self.lineNumber = lineNumber
        # This one is empty in this part of the process
        self.dependencyType = dependencyType
        # this is a map indexed by string (name of the category)
        # and containing an onother dict inside
        self.dimensionalDescriptors = {}

# subclass JSONEncoder


class JSONOutputElementEncoder(JSONEncoder):
    def default(self, je):
        d = {}
        d['methodName'] = je.name
        d['lineNumber'] = je.lineNumber
        d['dependencyType'] = je.dependencyType
        d['dimensionDescriptors'] = {}
        for a, b in je.dimensionalDescriptors.items():
            d['dimensionDescriptors'][a] = b
        return d


'''
These functions perform the main work. The input directory contains data (.dat) and figures (.eps)
for our results. For the same element, only one .dat and one .eps exist. With this, for each entry we 
read the data file, calculate some distance measure and bucket the result into some function bucket, 
this is, all closer to f1(x) go together while all closer to f2(x) go together.
'''

'''
Read experimental data from a .dat file. If the file has 3 columns, is two dimensional.
Otherwise is 1 dimensional.
'''


def parseExperimentalDataFromFile(file, fitter, normalizeOutput, maximumXAxisValue, isRaw=False):
    with open(file) as fp:
        lines = fp.readlines()
        firstLine = lines[0]
        count = len(lines[0].split(CSV_SEPARATOR))
        if count == 2:
            return parseSingleDimesionalExperimentalDataFromFile(fitter, normalizeOutput, maximumXAxisValue, lines, isRaw)
        elif count == 3:
            return parseTwoDimesionalExperimentalDataFromFile(fitter, normalizeOutput, maximumXAxisValue, lines, isRaw)
        else:
            LOG_LINE(
                'File [{:s}] has [{:d}] dimensions and cannot be parsed...', file, count)
            return None


def parseSingleDimesionalExperimentalDataFromFile(fitter, normalizeOutput, maximumXAxisValue, rows, isRaw):
    x = []
    y = []
    raw = []
    possibleLinearFactor = 0
    limit = 0 if isRaw is False else -1
    for line in rows:
        d1, d2 = int(line.split(CSV_SEPARATOR)[0]), float(
            line.split(CSV_SEPARATOR)[1])
        # notice that here we are ignoring the first value from the curve just when
        # there are enough values
        if d2 > limit:
            x.append(d1)
            y.append(d2)
            raw.append(d2)
            # now lets try to see if there is a factor
            possibleLinearFactor = possibleLinearFactor + \
                (d2 / (d1 if d1 > 0 else 1))
    experimentalData = np.zeros((len(x), 2))
    experimentalData[:, 0] = x
    # also record the maximum y value here
    maxY = max(y)
    maxX = max(x)
    avgLinearFactor = possibleLinearFactor / len(x)
    experimentalData[:, 1] = fitter.normalizeAxis(
        y) if normalizeOutput == True else y
    factoredData = list(map(lambda x: x / avgLinearFactor, y))
    return experimentalData, maxX, maxY, avgLinearFactor, factoredData, raw


def parseTwoDimesionalExperimentalDataFromFile(fitter, normalizeOutput, maximumXAxisValue, rows, isRaw):
    x = []
    y = []
    z = []
    raw = []
    possibleLinearFactor = 0
    limit = 0 if isRaw is False else -1
    for line in rows:
        d1, d2, d3 = int(line.split(CSV_SEPARATOR)[0]), int(
            line.split(CSV_SEPARATOR)[1]), float(line.split(CSV_SEPARATOR)[2])
        # notice that here we are ignoring the first value from the curve just when
        # there are enough values
        if d3 > limit:
            x.append(d1)
            y.append(d2)
            z.append(d3)
            raw.append(d3)
            # now lets try to see if there is a factor
            possibleLinearFactor = possibleLinearFactor + \
                (d3 / (d1*d2 if d1*d2 > 0 else 1))
    experimentalData = np.zeros((len(x), 3))
    experimentalData[:, 0] = x
    experimentalData[:, 1] = y
    # also record the maximum y value here
    maxY = max(z)
    maxX = maximumXAxisValue * 2
    avgLinearFactor = possibleLinearFactor / len(x)
    experimentalData[:, 2] = fitter.normalizeAxis(
        z) if normalizeOutput == True else z
    # return the factored data too to make sure
    factoredData = list(map(lambda x: x / avgLinearFactor, z))
    return experimentalData, maxX, maxY, avgLinearFactor, factoredData, raw


'''
Get a set with all the sd elements in a folder
'''


def parseSDElementsFromDirectory(inputDirectory):
    sdElements = {}
    LOG_LINE('Reading files from [{:s}]', inputDirectory)
    for f in os.listdir(inputDirectory):
        name = f[0: f.rindex('.')]
        e = SDElement(name)
        if e.name not in sdElements:
            sdElements[e.name] = e
    LOG_LINE('{:d} sd elements read from [{:s}]', len(
        sdElements), inputDirectory)
    return sdElements


'''
Bucketize the sd elements into different function buckets. Return the bucket set...
'''


def processSDElements(inputDirectory, fitter, functions, normalizeOutput, distanceMeasure, maximumXAxisValue):
    buckets = {}
    results = {}
    sdElements = parseSDElementsFromDirectory(inputDirectory)
    for _, e in sdElements.items():
        experimentalData, maxX, maxY, avgLinearFactor, factoredData, rawData = parseExperimentalDataFromFile(
            join(inputDirectory, e.dataFile), fitter, normalizeOutput, maximumXAxisValue)
        # we want someting to have a minimum of minDp to be able to make a decision
        minDp = round(maximumXAxisValue * BURSTY_PERCENTAGE)
        if len(experimentalData[:, 0]) > minDp:
            sortedFits, allFits, allScores = fitter.chooseBestFit(
                functions, experimentalData, normalizeOutput, distanceMeasure, e.name, rawData)
            # this is the best fit, a dictionary with the function name as key
            # and the data as value. This is size one always.
            bestFit = allFits[sortedFits[0]][0]
            allBestFits = allFits[sortedFits[0]]
            # store this too, since we need it for future plotting.
            e.bestFits = allBestFits
            e.experimentalData = experimentalData
            e.bestFitValue = sortedFits[0]
            # a set of data that LOOKS super linear but in reality no datapoint exceeds the
            # max dimension CANNOT be super linear, right?. Also, it has to be more than just one...
            couldBeSuperLinearCount = 0
            for d in rawData:
                if d > maximumXAxisValue:
                    couldBeSuperLinearCount = couldBeSuperLinearCount + 1
            e.couldbeSuperLinear = True if couldBeSuperLinearCount > 1 else False
            # store this avg factor, we want to use it later in case this is classified as linear
            e.avgLinearFactor = avgLinearFactor
            # store the factored data for possible regression later
            e.factoredData = factoredData
            # and now associate to a bucket.
            for name, data in bestFit.items():
                if name in buckets:
                    buckets[name].append(e)
                else:
                    buckets[name] = [e]
        else:
            LOG_LINE('[{:s}] only has {:d} < {:d} * {:f} = {:d} datapoints, it will be classified as bursty since there is no certainty of its category',
                     e.name, len(experimentalData[:, 0]), maximumXAxisValue, BURSTY_PERCENTAGE,  minDp)
            e.experimentalData = experimentalData
            e.bestFitValue = -1
            if BURSTY_LINEAR_FUNCTION_NAME in buckets:
                buckets[BURSTY_LINEAR_FUNCTION_NAME].append(e)
            else:
                buckets[BURSTY_LINEAR_FUNCTION_NAME] = [e]
    return buckets


'''
Given an sdElement (after processing), create a little plot showin the best fit. This works for single dimension.
'''


def plotSingleDimensionFittedSDElement(processedSDElement, savePath, distanceMeasureName, axisLabels):
    # just one subplot is fine here
    figure, ax = plt.subplots(1, 1)
    normalizer = SimilarityFit()
    if NORMALIZE_FOR_PLOTTING:
        processedSDElement.experimentalData[:, 1] = normalizer.normalizeAxis(
            processedSDElement.experimentalData[:, 1])
    ax.plot(processedSDElement.experimentalData[:, 0], processedSDElement.experimentalData[:,
            1], EXPERIMENT_COLOR, marker='o', linewidth=EXPERIMENT_LW, label=EXPERIMENT_LBL)
    # now also plot the bes fits, which come as a list of size 1 dicts
    bestFits = processedSDElement.bestFits
    currentColor = 0
    if bestFits is not None:
        for bestFit in bestFits:
            # each one is a size one dic
            for name, data in bestFit.items():
                newName = name if processedSDElement.newFunctionLabel is None or currentColor > 0 else processedSDElement.newFunctionLabel
                if NORMALIZE_FOR_PLOTTING:
                    data[:, 1] = normalizer.normalizeAxis(data[:, 1])
                ax.plot(data[:, 0], data[:, 1], FIT_COLORS[currentColor], linewidth=FIT_LW, label='[{:s} = {:.4f}] [B]'.format(
                    newName, processedSDElement.bestFitValue) if currentColor == 0 else '[{:s} = {:.4f}]'.format(newName, processedSDElement.bestFitValue))
            currentColor = currentColor + 1
            # just the best fit here, nothing else
            break
    # now we are done plotting, we can save the image as a png
    ax.legend()
    figure.suptitle('[{:s}] {:s}'.format(distanceMeasureName,
                    processedSDElement.name),  wrap=True, fontsize=8)
    ax.set_xlabel("x = #{:s}".format(axisLabels[0]))
    ax.set_ylabel("y = #{:s} [N]".format(axisLabels[1]))
    DEBUG_LINE('Saving [{:s}]', savePath)
    figure.savefig(savePath)
    # close the figure too
    plt.close(figure)


'''
Given an sdElement (after processing), create a little plot showin the best fit. This works for two dimensions.
'''


def plotTwoDimensionalFittedSDElement(processedSDElement, savePath, distanceMeasureName, axisLabels):
    # just one subplot is fine here
    figure, ax = plt.subplots(1, 1)
    ax = plt.axes(projection='3d')
    normalizer = SimilarityFit()
    if NORMALIZE_FOR_PLOTTING:
        processedSDElement.experimentalData[:, 2] = normalizer.normalizeAxis(
            processedSDElement.experimentalData[:, 2])
    ax.plot(processedSDElement.experimentalData[:, 0], processedSDElement.experimentalData[:, 1],
            processedSDElement.experimentalData[:, 2], EXPERIMENT_COLOR, marker='o', linewidth=EXPERIMENT_LW, label=EXPERIMENT_LBL)
    # now also plot the bes fits, which come as a list of size 1 dicts
    bestFits = processedSDElement.bestFits
    currentColor = 0
    if bestFits is not None:
        for bestFit in bestFits:
            # each one is a size one dic
            for name, data in bestFit.items():
                newName = name if processedSDElement.newFunctionLabel is None or currentColor > 0 else processedSDElement.newFunctionLabel
                if NORMALIZE_FOR_PLOTTING:
                    data[:, 2] = normalizer.normalizeAxis(data[:, 2])
                ax.plot(data[:, 0], data[:, 1], data[:, 2], FIT_COLORS[currentColor], linewidth=FIT_LW, label='[{:s} = {:.4f}] [B]'.format(
                    newName, processedSDElement.bestFitValue) if currentColor == 0 else '[{:s} = {:.4f}]'.format(newName, processedSDElement.bestFitValue))
            currentColor = currentColor + 1
            # just the best fit here, nothing else
            break
    # now we are done plotting, we can save the image as a png
    ax.legend()
    figure.suptitle('[{:s}] {:s}'.format(distanceMeasureName,
                    processedSDElement.name),  wrap=True, fontsize=8)
    ax.set_xlabel("x = #{:s}".format(axisLabels[0]))
    ax.set_ylabel("y = #{:s}".format(axisLabels[1]))
    ax.set_zlabel("z = #{:s} [N]".format(axisLabels[2]))
    DEBUG_LINE('Saving [{:s}]', savePath)

    figure.savefig(savePath)
    # close the figure too
    plt.close(figure)


'''
Handles single dimension parsing and plotting
'''


def handleSingleDimensionalExecution(inputFolder, outputFolder, distanceMeasureName, maximumXAxisValue, axisLabels, descriptorCSV):
    # process all the inout directory and get the best fits for each experiment.
    utils = TargetFunctions()
    similarityFitter = SimilarityFit()
    buckets = processSDElements(inputFolder, similarityFitter, utils.functions,
                                NORMALIZE_FOR_COMPARISON, utils.measures[distanceMeasureName], maximumXAxisValue)
    categoryBuckets = {}
    for name, elements in buckets.items():
        categoryName = utils.mapFunctionCategory(name)
        if categoryName is None:
            LOG_LINE(
                '[ERROR] [%s] was not mapped to a category. This is a bad error thus we stop here...')
            sys.exit(1)
        if categoryName not in categoryBuckets:
            categoryBuckets[categoryName] = elements
        else:
            for e in elements:
                categoryBuckets[categoryName].append(e)
    # now clean up a little to make super super linears are not over estimated...
    if SUPER_LINEAR_CATEGORY_NAME in categoryBuckets:
        bursty = []
        for e in categoryBuckets[SUPER_LINEAR_CATEGORY_NAME]:
            # cannot be super linear if its not large...
            if e.couldbeSuperLinear == False:
                LOG_LINE('[{:s}] was marked as super linear BUT maxY < maxX, thus it cannot be true. Reclassifing as [{:s}]',
                         e.name, BURSTY_LINEAR_CATEGORY_NAME)
                e.bestFits = None
                bursty.append(e)
        for x in bursty:
            categoryBuckets[SUPER_LINEAR_CATEGORY_NAME].remove(x)
        if len(categoryBuckets[SUPER_LINEAR_CATEGORY_NAME]) == 0:
            categoryBuckets.pop(SUPER_LINEAR_CATEGORY_NAME)
        if BURSTY_LINEAR_CATEGORY_NAME in categoryBuckets:
            for x in bursty:
                categoryBuckets[BURSTY_LINEAR_CATEGORY_NAME].append(x)
        else:
            categoryBuckets[BURSTY_LINEAR_CATEGORY_NAME] = bursty
    # the same type of clean up for the linears. Some are inline with the dimension, others involve some kind of
    # large constant. Lets see how the look like
    if LINEAR_CATEGORY_NAME in categoryBuckets:
        constantLinear = []
        supraLinear = []
        burstyLinear = []
        for e in categoryBuckets[LINEAR_CATEGORY_NAME]:
            # so here, everything is linear and we have the raw data, a constant linear factor that could be large and
            # the raw data AFTER that. If we eliminate the factor and calculate the slope, what happens if we get something
            # really small?
            if e.avgLinearFactor > 1:
                slope = np.polyfit(
                    e.experimentalData[:, 0], e.factoredData, 1)[0]
                # This is slow growth
                if slope < SLOW_GROWTH:
                    LOG_LINE(
                        '[{:s}] was marked as linear, but its growth rate is too small ({:.2f} < {:.2f}), thus it will be downgraded...', e.name, slope, SLOW_GROWTH)
                    e.newFunctionLabel = '{:.4f}x'.format(slope)
                    supraLinear.append(e)
                # this was linear, but there are only two points so we dont know...
                elif len(e.experimentalData[:, 0]) <= SMALL_DATA_POINT_SIZE:
                    LOG_LINE('[{:s}] was marked as linear, it only has [{:d}] datapoints, so it will be reclassified...', e.name, len(
                        e.experimentalData[:, 0]))
                    burstyLinear.append(e)
                # is not in any of these categories, so at the end of the day is linear with some factor. How large is this factor? If its the same or one order of magnitude larger
                # than the maximum size, then it could be concerning
                elif len(str(int(e.avgLinearFactor))) >= SIGNIFICANT_MULTIPLIER_DIGITS:
                    LOG_LINE(
                        '[{:s}] was marked as linear, but the growth factor seems to be significant ([{:.0f}]), thus it will be upgraded...', e.name, e.avgLinearFactor)
                    e.newFunctionLabel = '{:d}x'.format(int(e.avgLinearFactor))
                    constantLinear.append(e)
               # so now we clean
        for e1 in constantLinear:
            categoryBuckets[LINEAR_CATEGORY_NAME].remove(e1)
        for e1 in supraLinear:
            categoryBuckets[LINEAR_CATEGORY_NAME].remove(e1)
        for e1 in burstyLinear:
            categoryBuckets[LINEAR_CATEGORY_NAME].remove(e1)
        if len(categoryBuckets[LINEAR_CATEGORY_NAME]) == 0:
            categoryBuckets.pop(LINEAR_CATEGORY_NAME)
        # and append the new categories
        if len(constantLinear) > 0:
            categoryBuckets[CONSTANT_LINEAR_CATEGORY_NAME] = constantLinear
        if len(supraLinear) > 0:
            if SUPRA_CATEGORY_NAME in categoryBuckets:
                for x in supraLinear:
                    categoryBuckets[SUPRA_CATEGORY_NAME].append(x)
            else:
                categoryBuckets[SUPRA_CATEGORY_NAME] = supraLinear
        if len(burstyLinear) > 0:
            if BURSTY_LINEAR_CATEGORY_NAME in categoryBuckets:
                for x in burstyLinear:
                    categoryBuckets[BURSTY_LINEAR_CATEGORY_NAME].append(x)
            else:
                categoryBuckets[BURSTY_LINEAR_CATEGORY_NAME] = burstyLinear
    # print the summary
    LOG_LINE('Summary: obtained {:d} buckets, detail is: ', len(
        categoryBuckets))
    for name, elements in categoryBuckets.items():
        LOG_LINE('[{:s}] with [{:d}] elements', name, len(elements))
    outputName = join(outputFolder, distanceMeasureName)
    LOG_LINE('Copying results to [{:s}]', outputName)
    if os.path.exists(outputName):
        LOG_LINE(
            "Could not write results to [{:s}], directory exists!", outputName)
        sys.exit(1)
    else:
        # save the results
        saveResults(outputName, categoryBuckets, utils, axisLabels,
                    plotSingleDimensionFittedSDElement, descriptorCSV, maximumXAxisValue, inputFolder)


'''
Handles two dimension parsing and plotting
'''


def handleTwoDimensionalExecution(inputFolder, outputFolder, distanceMeasureName, maximumXAxisValue, axisLabels, descriptorCSV):
    # process all the inout directory and get the best fits for each experiment.
    utils = TargetFunctions()
    similarityFitter = SimilarityFit()
    buckets = processSDElements(inputFolder, similarityFitter, utils.twoDimFunctions,
                                NORMALIZE_FOR_COMPARISON, utils.measures[distanceMeasureName], maximumXAxisValue)
    categoryBuckets = {}
    for name, elements in buckets.items():
        categoryName = utils.mapFunctionCategory(name)
        if categoryName is None:
            LOG_LINE(
                '[ERROR] [%s] was not mapped to a category. This is a bad error thus we stop here...')
            sys.exit(1)
        if categoryName not in categoryBuckets:
            categoryBuckets[categoryName] = elements
        else:
            for e in elements:
                categoryBuckets[categoryName].append(e)
    # now clean up a little to make super super linears are not over estimated...
    if SUPER_LINEAR_CATEGORY_NAME in categoryBuckets:
        bursty = []
        for e in categoryBuckets[SUPER_LINEAR_CATEGORY_NAME]:
            if e.couldbeSuperLinear == False:
                LOG_LINE('[{:s}] was marked as super linear BUT maxY < 2*[{:d}] = [{:d}], thus it cannot be true. Reclassifing as [{:s}]',
                         e.name, maximumXAxisValue, 2 * maximumXAxisValue, BURSTY_LINEAR_CATEGORY_NAME)
                bursty.append(e)
        for e in bursty:
            categoryBuckets[SUPER_LINEAR_CATEGORY_NAME].remove(e)
        if len(bursty) > 0:
            if BURSTY_LINEAR_CATEGORY_NAME not in categoryBuckets:
                categoryBuckets[BURSTY_LINEAR_CATEGORY_NAME] = bursty
            else:
                for x in bursty:
                    categoryBuckets[BURSTY_LINEAR_CATEGORY_NAME].append(x)
    # print the summary
    LOG_LINE('Summary: obtained {:d} buckets, detail is: ', len(
        categoryBuckets))
    for name, elements in categoryBuckets.items():
        LOG_LINE('[{:s}] with [{:d}] elements', name, len(elements))
    outputName = join(outputFolder, distanceMeasureName)
    LOG_LINE('Copying results to [{:s}]', outputName)
    if os.path.exists(outputName):
        LOG_LINE(
            "Could not write results to [{:s}], directory exists!", outputName)
        sys.exit(1)
    else:
        # save the results
        saveResults(outputName, categoryBuckets, utils, axisLabels,
                    plotTwoDimensionalFittedSDElement, descriptorCSV, maximumXAxisValue, inputFolder)


'''
Reads the CSV descriptors to create a mapping between the type 
'''


def loadApplicationLoops(file):
    if os.path.exists(file):
        loops = set()
        with open(file) as fp:
            lines = fp.readlines()
            for line in lines:
                pieces = line.split(CSV_SEPARATOR)
                s = '{:s}.{:s}'.format(pieces[0], pieces[1])
                loops.add(s)
        return loops
    LOG_LINE(
        'Apllication loops file [{:s}] was not found, returning empty set...', file)
    return set()


'''
Store the results, pictures csv summary and json output.
'''


def saveResults(outputName, categoryBuckets, utils, dimensionNames, plotFunction, descriptorCSV, maximumXAxisValue, inputName):
    os.makedirs(outputName)
    # We will also write a summary
    summaryFile = join(outputName, SUMMARY_NAME)
    # this is for json elements
    jsonFile = join(outputName, JSON_NAME)
    jsonElements = []
    LOG_LINE('Summary will be saved to [{:s}]', summaryFile)
    LOG_LINE('JSON elements will be saved to [{:s}]', jsonFile)
    LOG_LINE('Descriptors will be loaded from [{:s}]', descriptorCSV)
    descriptors = loadApplicationLoops(descriptorCSV)
    f = open(summaryFile, "w")
    f.write('{:s}\n'.format(SUMMARY_COLUMNS))
    # also, make one folder for each category with values
    for category in utils.categories.keys():
        if category in categoryBuckets:
            os.makedirs(join(outputName, category))
    for name, values in categoryBuckets.items():
        bucketName = join(join(outputName, name))
        for e in values:
            # here we plot
            path = join(bucketName, e.plotFile)
            # but before plotting, if this is bursty we will plot the original
            if name == BURSTY_LINEAR_CATEGORY_NAME:
                rawData, _, _, _, _, _ = parseExperimentalDataFromFile(join(
                    inputName, e.rawDataFile), SimilarityFit(), False, maximumXAxisValue, isRaw=True)
                e.experimentalData = rawData
            plotFunction(e, path, distanceMeasureName, axisLabels)
            method = e.name[: e.name.rindex('.')]
            line = e.name[e.name.rindex('.') + 1:]
            if e.bestFits is not None:
                complexity = list(e.bestFits[0].items())[0][0]
                complexity = complexity if e.newFunctionLabel is None else e.newFunctionLabel
                complexity = complexity.replace('x', axisLabels[0])
                if len(axisLabels) == 3:
                    complexity = complexity.replace('y', axisLabels[1])
                f.write('{:s}{:s}{:s}{:s}{:s}{:s}{:s}\n'.format(
                    method, CSV_SEPARATOR, line, CSV_SEPARATOR, name, CSV_SEPARATOR, complexity))
            else:
                complexity = BURSTY_LINEAR_COMP_NAME if len(
                    axisLabels) == 3 else BURSTY_LINEAR_COMP_NAME_SINGLE
                complexity = complexity.replace('x', axisLabels[0])
                if len(axisLabels) == 3:
                    complexity = complexity.replace('y', axisLabels[1])
                f.write('{:s}{:s}{:s}{:s}{:s}{:s}{:s}\n'.format(
                    method, CSV_SEPARATOR, line, CSV_SEPARATOR, name, CSV_SEPARATOR, complexity))
            # and store the json element too
            je = JSONOutputElement(method, int(
                line), LOOP_TYPE if e.name in descriptors else CROSS_TYPE)
            je.dimensionalDescriptors[name] = [complexity]
            jsonElements.append(je)
    # and close the file
    f.close()
    # and write the json
    with open(jsonFile, 'w') as f:
        json.dump(jsonElements, f, indent=2, cls=JSONOutputElementEncoder)


'''
This function is used to merge existing json element files into a single thing.
'''


def mergeJSONElements(inputPaths, outputPath):
    indexedData = {}
    for path in inputPaths:
        with open(path, 'r') as f:
            data = json.load(f)
            for element in data:
                id = '{:s}.{:d}'.format(
                    element['methodName'], element['lineNumber'])
                if id not in indexedData:
                    indexedData[id] = element
                else:
                    for name, values in element['dimensionDescriptors'].items():
                        if name in indexedData[id]['dimensionDescriptors']:
                            for v in values:
                                if v not in indexedData[id]['dimensionDescriptors'][name]:
                                    indexedData[id]['dimensionDescriptors'][name].append(
                                        v)
                        else:
                            indexedData[id]['dimensionDescriptors'][name] = values
    # Now save
    savePath = join(outputPath, SD_MERGED_NAME)
    LOG_LINE('Done merging files, saving results to [{:s}]', savePath)
    objects = []
    for _, x in indexedData.items():
        objects.append(x)
    with open(savePath, 'w') as f:
        json.dump(objects, f, indent=2, cls=JSONOutputElementEncoder)


'''
This function is used to clean json files for only some categories.
'''


def doCleanJson(path, keep_cat, keep_type, output):
    elements = []
    total = 0
    with open(path, 'r') as f:
        data = json.load(f)
        for element in data:
            total = total + 1
            if element['dependencyType'] not in keep_type:
                continue
            else:
                to_pop = []
                for category in element['dimensionDescriptors'].keys():
                    if category not in keep_cat:
                        to_pop.append(category)
                for p in to_pop:
                    element['dimensionDescriptors'].pop(p)
                if len(element['dimensionDescriptors']) == 0:
                    continue
                else:
                    elements.append(element)
    # Output to screen first
    for element in elements:
        print('{:s}.{:d}'.format(element['methodName'], element['lineNumber']))
    # Now save
    savePath = output
    LOG_LINE('Done scanning file found [{:d}] matches out of [{:d}], saving results to [{:s}]', len(
        elements), total, savePath)
    with open(savePath, 'w') as f:
        json.dump(elements, f, indent=2, cls=JSONOutputElementEncoder)


if __name__ == '__main__':
    workload = sys.argv[1]
    if workload == DO_MERGE:
        try:
            paths = sys.argv[2: -1]
            output = sys.argv[-1]
            LOG_LINE(
                'Merging files at {:s} and saving results into {:s}', str(paths), output)
        except:
            LOG_LINE(
                'USAGE: <list of json file paths : paths> <outputFolder : path>')
            sys.exit(1)
        mergeJSONElements(paths, output)
    elif workload == DO_CLEAN:
        try:
            path = sys.argv[2]
            keep_cat = sys.argv[3].split(',')
            keep_type = sys.argv[4].split(',')
            output = path + '.clean'
        except:
            LOG_LINE(
                'USAGE: <input-json> <complexit categories to keep> <types to keep>')
            sys.exit(1)
        LOG_LINE('Cleaning {:s}, keeping [{:s}] and [{:s}], saving into [{:s}]', path, str(
            keep_cat), str(keep_type), output)
        doCleanJson(path, keep_cat, keep_type, output)
    elif workload == DO_DISTANCES:
        inputFolder = None
        outputFolder = None
        distanceMeasureName = None
        maximumXAxisValue = None
        axisLabels = None
        try:
            inputFolder = sys.argv[2]
            outputFolder = sys.argv[3]
            distanceMeasureName = sys.argv[4]
            maximumXAxisValue = int(sys.argv[5])
            numDimensions = int(sys.argv[6])
            axisLabels = sys.argv[7].split(CSV_SEPARATOR)
            plotFolder = inputFolder
            descriptorCSV = sys.argv[8]
            LOG_LINE('Parsing files in [{:s}]. Output will be written to [{:s}], distance measure to use is [{:s}]. Processing for [{:d}] dimensions with labels={:s}. Types will be taken from [{:s}].',
                     plotFolder, outputFolder, distanceMeasureName, numDimensions, str(axisLabels), descriptorCSV)
            if distanceMeasureName not in DISTANCE_NAMES:
                LOG_LINE("[{:s}] is not a valid distance measure. Use one of [{:s}]",
                         distanceMeasureName, str(DISTANCE_NAMES))
                sys.exit(1)
        except:
            LOG_LINE('USAGE: <inputFolder : path> <outputFolder : path> <distanceMeasureName : string> <maximumXAxisValue : int> <numDimensions : int> <axisLabels : comma sep string> <descriptor_csv : path>')
            sys.exit(1)
        if numDimensions > 2 or numDimensions <= 0:
            LOG_LINE(
                'numDimensions must be either [1,2], [{:d}] requested, exiting...', numDimensions)
            sys.exit(1)
        if numDimensions + 1 != len(axisLabels):
            LOG_LINE(
                'Axis labels are improperly set: need [{:d}] for [{:d}] dimensions, but got [{:d}], exiting...', numDimensions + 1, numDimensions, len(axisLabels))
            sys.exit(1)
        if numDimensions == 2 and distanceMeasureName not in N_DIM_DISTANCES:
            LOG_LINE('For [{:d}] dimensions, only [{:s}] measures are allowed, exiting...',
                     numDimensions, str(N_DIM_DISTANCES))
            sys.exit(1)
        if numDimensions == 1:
            LOG_LINE('Handling single dimensional process...')
            handleSingleDimensionalExecution(
                plotFolder, outputFolder, distanceMeasureName, maximumXAxisValue, axisLabels, descriptorCSV)
        elif numDimensions == 2:
            LOG_LINE('Handling two dimensional process...')
            handleTwoDimensionalExecution(
                plotFolder, outputFolder, distanceMeasureName, maximumXAxisValue, axisLabels, descriptorCSV)
        else:
            LOG_LINE('Error, too many dimensions, nothing handled....')
    else:
        LOG_LINE('Workload (first argument) was not matched....')
    LOG_LINE('Done...')
